package app.goonreader.data.repo

import app.goonreader.data.db.HistoryDao
import app.goonreader.data.db.HistoryEntity
import app.goonreader.data.db.HistoryRow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reading history (PRD 9.4). One row per reading session: the reader opens a session when a
 * chapter loads and updates its duration when the session ends. Incognito is enforced by the
 * reader view model, which simply never calls [start].
 */
@Singleton
class HistoryRepository @Inject constructor(private val dao: HistoryDao) {

    fun observe(): Flow<List<HistoryRow>> = dao.observeRows(HISTORY_LIMIT)

    suspend fun start(workId: String, chapterId: String, at: Long = System.currentTimeMillis()): Long =
        dao.insert(HistoryEntity(workId = workId, chapterId = chapterId, lastReadAt = at))

    suspend fun addReadingTime(entryId: Long, durationMs: Long) {
        dao.touch(entryId, System.currentTimeMillis(), durationMs.coerceAtLeast(0L))
    }

    suspend fun delete(entryId: Long) = dao.delete(entryId)

    suspend fun clear() = dao.clearAll()

    private companion object {
        /** Recent activity, not an archive; the oldest rows fall off. */
        const val HISTORY_LIMIT = 300
    }
}
