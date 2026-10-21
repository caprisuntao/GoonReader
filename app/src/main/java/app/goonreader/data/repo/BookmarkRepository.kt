package app.goonreader.data.repo

import app.goonreader.data.db.BookmarkDao
import app.goonreader.data.db.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved reading positions (PRD 10.9).
 *
 * A bookmark is a chapter plus a fraction, so restoring it is the same path as restoring
 * progress. Two bookmarks closer than [NEAR] in the same chapter are treated as the same
 * position: tapping the bookmark icon twice toggles it instead of piling up duplicates.
 */
@Singleton
class BookmarkRepository @Inject constructor(private val dao: BookmarkDao) {

    fun observe(workId: String): Flow<List<BookmarkEntity>> = dao.observeForWork(workId)

    fun findNear(bookmarks: List<BookmarkEntity>, chapterId: String, progress: Float): BookmarkEntity? =
        bookmarks.firstOrNull { it.chapterId == chapterId && kotlin.math.abs(it.progress - progress) < NEAR }

    suspend fun add(
        workId: String,
        chapterId: String,
        chapterNumber: Int,
        chapterTitle: String,
        progress: Float,
    ) {
        dao.insert(
            BookmarkEntity(
                workId = workId,
                chapterId = chapterId,
                chapterNumber = chapterNumber,
                chapterTitle = chapterTitle,
                progress = progress,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        /** Roughly one screen of text; near enough that "bookmark here" twice is a toggle. */
        const val NEAR = 0.02f
    }
}
