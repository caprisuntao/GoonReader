package app.goonreader.data.download

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Chapter text the user explicitly downloaded (PRD 4).
 *
 * Plain gzipped files in a directory of their own: the LRU cache may evict anything it likes
 * without touching these, which is what "downloads are never evicted" needs. Same payload as
 * the cache ([app.goonreader.data.cache.ChapterCodec]), so a downloaded chapter and a cached
 * one decode through one path.
 */
class DownloadStore(directory: File) {

    private val dir = directory.also { it.mkdirs() }

    @Synchronized
    fun read(workId: String, chapterId: String): String? = runCatching {
        val file = file(workId, chapterId)
        if (!file.exists()) return null
        GZIPInputStream(file.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    @Synchronized
    fun contains(workId: String, chapterId: String): Boolean = file(workId, chapterId).exists()

    /** Write-then-rename so a killed process never leaves a half-written download behind. */
    @Synchronized
    fun write(workId: String, chapterId: String, html: String) {
        dir.mkdirs()
        val target = file(workId, chapterId)
        val temp = File(dir, target.name + ".tmp")
        try {
            GZIPOutputStream(temp.outputStream()).use { it.write(html.toByteArray(Charsets.UTF_8)) }
            // Files.move replaces the destination; File.renameTo does not, on every platform.
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    @Synchronized
    fun remove(workId: String, chapterId: String) {
        file(workId, chapterId).delete()
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Used by "clear downloads" in Data and storage. */
    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Ids arrive from AO3 pages; keep them path-safe rather than trusting that. */
    private fun file(workId: String, chapterId: String) =
        File(dir, "w${safe(workId)}c${safe(chapterId)}.html.gz")

    private fun safe(id: String) = id.filter { it.isLetterOrDigit() }
}
