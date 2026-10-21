package app.goonreader.data.cache

import com.jakewharton.disklrucache.DiskLruCache
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Sanitised chapter HTML for recently opened chapters (PRD 4: cache, not library, not downloads).
 *
 * Entries are gzipped and evicted least-recently-used once the limit is exceeded. DiskLruCache
 * owns the journaling and the eviction order; this class only maps (workId, chapterId) to a key
 * and does the gzip. Downloads have their own directory and never share this budget.
 *
 * DiskLruCache is not thread safe, hence the synchronized accessors. The limit can change at
 * runtime (PRD 9.8) and [clear] reopens the journal so the instance stays usable.
 */
class ChapterCache(private val directory: File, maxBytes: Long = DEFAULT_MAX_BYTES) {

    private var lru = DiskLruCache.open(directory, 1, 1, maxBytes)

    @Synchronized
    fun read(workId: String, chapterId: String): String? = runCatching {
        lru.get(key(workId, chapterId))?.use { entry ->
            GZIPInputStream(entry.getInputStream(0)).use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }.getOrNull()

    @Synchronized
    fun contains(workId: String, chapterId: String): Boolean =
        runCatching { lru.get(key(workId, chapterId))?.use { true } ?: false }.getOrDefault(false)

    @Synchronized
    fun write(workId: String, chapterId: String, html: String) {
        runCatching {
            val editor = lru.edit(key(workId, chapterId)) ?: return
            try {
                GZIPOutputStream(editor.newOutputStream(0)).use {
                    it.write(html.toByteArray(Charsets.UTF_8))
                }
                editor.commit()
            } catch (e: IOException) {
                editor.abort()
                throw e
            }
            lru.flush()
        }
    }

    @Synchronized
    fun remove(workId: String, chapterId: String) {
        runCatching { lru.remove(key(workId, chapterId)) }
    }

    @Synchronized
    fun setMaxBytes(bytes: Long) {
        runCatching { lru.maxSize = bytes }
    }

    @Synchronized
    fun sizeBytes(): Long = runCatching { lru.size() }.getOrDefault(0L)

    /** Evicts everything by deleting and reopening the journal with the same cap. */
    @Synchronized
    fun clear() {
        val cap = runCatching { lru.maxSize }.getOrDefault(DEFAULT_MAX_BYTES)
        runCatching { lru.delete() }
        lru = DiskLruCache.open(directory, 1, 1, cap)
    }

    /**
     * Keys must match DiskLruCache's `[a-z0-9_-]{1,120}`; AO3 ids are numeric, so the prefix is
     * all the namespacing this needs.
     */
    private fun key(workId: String, chapterId: String) = "w${workId}c${chapterId}"

    companion object {
        /** 40 MB holds several hundred chapters; PRD 4 only asks for a cap that eviction respects. */
        const val DEFAULT_MAX_BYTES = 40L * 1024 * 1024
    }
}
