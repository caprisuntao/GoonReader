package app.goonreader.data.repo

import app.goonreader.data.cache.ChapterCache
import app.goonreader.data.cache.ChapterCodec
import app.goonreader.data.db.ChapterDao
import app.goonreader.data.download.DownloadStore
import app.goonreader.data.network.Ao3Client
import app.goonreader.data.network.Ao3Page
import app.goonreader.data.network.Ao3Urls
import app.goonreader.data.parser.ChapterParser
import app.goonreader.domain.model.ChapterContent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves chapter text: download first, cache second, network last (PRD 6 `ChapterContentSource`).
 *
 * The reader goes through this one call, so it never knows — or cares — where the HTML came from.
 * A downloaded chapter is served with no network at all, which is the offline promise.
 */
@Singleton
class ChapterRepository @Inject constructor(
    private val client: Ao3Client,
    private val cache: ChapterCache,
    private val downloads: DownloadStore,
    private val chapterDao: ChapterDao,
) {

    suspend fun getChapter(workId: String, chapterId: String): RepoResult<ChapterContent> {
        downloaded(workId, chapterId)?.let { return RepoResult.Ok(it) }
        cached(workId, chapterId)?.let { return RepoResult.Ok(it) }

        // A one-shot's chapter id is the work id and has no /chapters/ page of its own.
        val url = if (chapterId == workId) {
            Ao3Urls.work(workId)
        } else {
            Ao3Urls.chapter(workId, chapterId)
        }

        val page = client.get(url)
        if (page !is Ao3Page.Success) return RepoResult.Failed(page.toFailureReason())

        val content = ChapterParser.parse(page.html, workId, chapterId)
            ?: return RepoResult.Failed(FailureReason.ParseError)

        cache.write(workId, chapterId, ChapterCodec.encode(content))
        return RepoResult.Ok(content)
    }

    /**
     * Warm the next chapter while the reader shows the current one (PRD 7.3: only the next one).
     * Best effort — a failed prefetch must never surface, the user has not asked for it yet.
     */
    suspend fun prefetch(workId: String, chapterId: String) {
        if (downloads.contains(workId, chapterId) || cache.contains(workId, chapterId)) return
        runCatching { getChapter(workId, chapterId) }
    }

    /** Persists position and flips the read flag once [markReadThreshold] is reached. */
    suspend fun saveProgress(chapterId: String, progress: Float, markReadThreshold: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        chapterDao.setProgress(chapterId, clamped)
        if (clamped >= markReadThreshold) chapterDao.setRead(chapterId, true)
    }

    private fun downloaded(workId: String, chapterId: String): ChapterContent? =
        downloads.read(workId, chapterId)?.let { ChapterCodec.decode(it, workId, chapterId) }

    private fun cached(workId: String, chapterId: String): ChapterContent? {
        val html = cache.read(workId, chapterId) ?: return null
        return ChapterCodec.decode(html, workId, chapterId)
    }
}
