package app.goonreader.data.download

import app.goonreader.data.cache.ChapterCodec
import app.goonreader.data.db.ChapterDao
import app.goonreader.data.repo.ChapterRepository
import app.goonreader.data.repo.RepoResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** One download batch, in UI terms: how far along, what failed, and what is being fetched now. */
data class DownloadState(
    val workId: String? = null,
    val total: Int = 0,
    val processed: Int = 0,
    val failed: List<String> = emptyList(),
    val currentChapterId: String? = null,
    val running: Boolean = false,
) {
    val hasFailures: Boolean get() = !running && failed.isNotEmpty()
}

/**
 * The download queue (PRD 9.9, minimal form): one batch at a time, in order, through the same
 * [ChapterRepository] the reader uses. A chapter already in the store is adopted, not refetched.
 *
 * `shortcut: the queue lives in the app process; a foreground service with a notification is
 * deferred until downloads get large enough to outlive the screen.`
 */
@Singleton
class DownloadManager @Inject constructor(
    private val store: DownloadStore,
    private val chapters: ChapterRepository,
    private val chapterDao: ChapterDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var job: Job? = null

    /** Starts a batch. A batch already running wins: the queue is serial. */
    fun enqueue(workId: String, chapterIds: List<String>) {
        if (_state.value.running || chapterIds.isEmpty()) return

        _state.value = DownloadState(workId = workId, total = chapterIds.size, running = true)
        job = scope.launch {
            val failed = mutableListOf<String>()
            chapterIds.forEachIndexed { index, chapterId ->
                _state.update { it.copy(currentChapterId = chapterId) }
                if (!store.contains(workId, chapterId) && !save(workId, chapterId)) failed += chapterId
                // The file is the truth; the flag only exists so lists can show a tick.
                if (store.contains(workId, chapterId)) chapterDao.setDownloaded(chapterId, true)
                _state.update { it.copy(processed = index + 1, failed = failed.toList()) }
            }
            _state.update { it.copy(running = false, currentChapterId = null) }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(running = false, currentChapterId = null) }
    }

    fun retryFailed() {
        val state = _state.value
        state.workId?.let { enqueue(it, state.failed) }
    }

    /** Removes stored text and clears the flags. Refused mid-batch, where it would race writes. */
    suspend fun deleteDownloads(workId: String, chapterIds: List<String>) {
        if (_state.value.running) return
        chapterIds.forEach { chapterId ->
            store.remove(workId, chapterId)
            chapterDao.setDownloaded(chapterId, false)
        }
    }

    /** Data and storage "clear downloads": every file and every flag. */
    suspend fun deleteAllDownloads() {
        if (_state.value.running) return
        store.clear()
        chapterDao.clearDownloadFlags()
    }

    private suspend fun save(workId: String, chapterId: String): Boolean {
        val content = when (val result = chapters.getChapter(workId, chapterId)) {
            is RepoResult.Ok -> result.value
            is RepoResult.Failed -> return false
        }
        return runCatching { store.write(workId, chapterId, ChapterCodec.encode(content)) }.isSuccess
    }
}
