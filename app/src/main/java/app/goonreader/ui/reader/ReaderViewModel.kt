package app.goonreader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import android.util.Log
import app.goonreader.data.db.BookmarkEntity
import app.goonreader.data.db.ChapterEntity
import app.goonreader.data.download.DownloadManager
import app.goonreader.data.prefs.AppPreferences
import app.goonreader.data.prefs.AppSettings
import app.goonreader.data.prefs.ReaderPreferences
import app.goonreader.data.prefs.ReaderSettings
import app.goonreader.data.reader.CustomFonts
import app.goonreader.data.repo.BookmarkRepository
import app.goonreader.data.repo.ChapterRepository
import app.goonreader.data.repo.FailureReason
import app.goonreader.data.repo.HistoryRepository
import app.goonreader.data.repo.RepoResult
import app.goonreader.data.repo.WorkRepository
import app.goonreader.data.tts.TtsChapter
import app.goonreader.data.tts.TtsController
import app.goonreader.data.tts.TtsState
import app.goonreader.domain.model.ChapterContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderChapter(
    val id: String,
    val number: Int,
    val title: String,
    val isRead: Boolean,
    val progress: Float,
)

data class ReaderUiState(
    val workTitle: String = "",
    val chapters: List<ReaderChapter> = emptyList(),
    val index: Int = 0,
    val chapterId: String = "",
    val chapterTitle: String = "",
    val content: ChapterContent? = null,
    /** Where to restore when [chapterId] first becomes current; 0 for a fresh chapter. */
    val initialProgress: Float = 0f,
    val isLoading: Boolean = true,
    val failure: FailureReason? = null,
    val settings: ReaderSettings = ReaderSettings(),
    /** PRD 10.5: immersive by default, the menu zone brings the bars back. */
    val chromeVisible: Boolean = false,
    val progress: Float = 0f,
    val finished: Boolean = false,
) {
    val hasNext: Boolean get() = index < chapters.lastIndex
    val hasPrev: Boolean get() = index > 0
    val currentChapter: ReaderChapter? get() = chapters.getOrNull(index)
}

/**
 * Reader state: which chapter is open, its text, and where the user is in it.
 *
 * Content comes from [ChapterRepository] (download → cache → network), so the reader never knows
 * or cares where the HTML came from. Chapter lists for works that are not in the library come
 * from a one-off fetch; reading a preview must not write to the library (PRD 4). History is only
 * written for library works, because the row needs a work to point at.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val workRepository: WorkRepository,
    private val chapterRepository: ChapterRepository,
    private val readerPreferences: ReaderPreferences,
    private val appPreferences: AppPreferences,
    private val historyRepository: HistoryRepository,
    private val downloads: DownloadManager,
    private val bookmarkRepository: BookmarkRepository,
    private val tts: TtsController,
    private val fonts: CustomFonts,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val workId: String = checkNotNull(savedStateHandle.get<String>("workId")) {
        "ReaderViewModel requires a workId route argument"
    }
    private val requestedChapterId: String? = savedStateHandle.get<String>("chapterId")

    /** Set when the reader was opened from a bookmark; -1 means "use the stored progress". */
    private var requestedProgress: Float = savedStateHandle.get<Float>("progress") ?: -1f

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> = bookmarkRepository.observe(workId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Read-aloud state, owned by the controller so it survives this screen. */
    val ttsState: StateFlow<TtsState> = tts.state

    private val _fontError = MutableStateFlow<String?>(null)
    val fontError: StateFlow<String?> = _fontError.asStateFlow()

    private var loadJob: Job? = null
    private var appSettings = AppSettings()
    private var workInLibrary = false

    // One history row per reading session; duration accumulates across pauses.
    private var historyEntryId: Long? = null
    private var historyOpenedAt = 0L
    private var historyMillis = 0L

    init {
        viewModelScope.launch {
            readerPreferences.settings.collect { settings -> _state.update { it.copy(settings = settings) } }
        }
        viewModelScope.launch {
            appPreferences.settings.collect { settings -> appSettings = settings }
        }
        viewModelScope.launch { start() }
    }

    fun toggleChrome() = _state.update { it.copy(chromeVisible = !it.chromeVisible) }

    fun hideChrome() = _state.update { it.copy(chromeVisible = false) }

    fun nextChapter() = openChapter(_state.value.index + 1)

    fun previousChapter() = openChapter(_state.value.index - 1)

    fun openChapterById(chapterId: String) {
        val index = _state.value.chapters.indexOfFirst { it.id == chapterId }
        if (index >= 0) openChapter(index)
    }

    fun retry() = openChapter(_state.value.index)

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { readerPreferences.update(transform) }
    }

    /** PRD 10.4: the `@font-face` rule for the imported font, empty for the system font. */
    fun fontCss(): String = fonts.css(_state.value.settings.customFontPath)

    fun importFont(uri: Uri) {
        val previous = _state.value.settings.customFontPath
        viewModelScope.launch {
            fonts.import(uri, previous)
                .onSuccess { path ->
                    _fontError.value = null
                    updateSettings { it.copy(customFontPath = path) }
                }
                .onFailure { failure ->
                    _fontError.value = failure.message ?: "Could not load that font."
                }
        }
    }

    fun clearFont() {
        val previous = _state.value.settings.customFontPath
        viewModelScope.launch {
            updateSettings { it.copy(customFontPath = "") }
            fonts.remove(previous)
            _fontError.value = null
        }
    }

    fun dismissFontError() {
        _fontError.value = null
    }

    /** Called continuously by the screen; only a settled position is written to the database. */
    fun onProgress(progress: Float, settled: Boolean) {
        val chapter = _state.value.currentChapter ?: return
        val threshold = _state.value.settings.markReadThreshold
        val finished = progress >= threshold
        val wasFinished = _state.value.finished

        _state.update { current ->
            // Rebuilding the list on every scroll frame would allocate for nothing; the rows
            // only change when a position is settled or the read flag flips.
            val updateRows = settled || finished != current.finished
            current.copy(
                progress = progress,
                finished = finished,
                chapters = if (!updateRows) {
                    current.chapters
                } else {
                    current.chapters.map {
                        if (it.id != chapter.id) {
                            it
                        } else {
                            it.copy(
                                progress = if (settled) progress else it.progress,
                                isRead = it.isRead || finished,
                            )
                        }
                    }
                },
            )
        }
        if (settled) {
            viewModelScope.launch {
                chapterRepository.saveProgress(chapter.id, progress, threshold)
            }
        }
        // PRD 9.8: delete the download once the chapter has been read.
        if (finished && !wasFinished && appSettings.deleteAfterReading) {
            viewModelScope.launch { downloads.deleteDownloads(workId, listOf(chapter.id)) }
        }
    }

    /** Saves the current position outside the scroll callback, e.g. on pause or back. */
    fun persistProgress() {
        val current = _state.value
        val chapter = current.currentChapter ?: return
        viewModelScope.launch {
            chapterRepository.saveProgress(chapter.id, current.progress, current.settings.markReadThreshold)
        }
        saveHistory(keepSession = true)
    }

    private suspend fun start() {
        val stored = workRepository.getWork(workId)
        workInLibrary = stored?.inLibrary == true
        var title = stored?.title.orEmpty()
        var chapters = workRepository.getChapters(workId).map { it.toReaderChapter() }

        if (chapters.isEmpty()) {
            // Not in the library: the details screen had this list in memory only.
            when (val bundle = workRepository.fetchWork(workId)) {
                is RepoResult.Ok -> {
                    title = bundle.value.work.title
                    chapters = bundle.value.chapters.map {
                        ReaderChapter(it.id, it.number, it.title, isRead = false, progress = 0f)
                    }
                }
                is RepoResult.Failed -> {
                    _state.update { it.copy(isLoading = false, failure = bundle.reason) }
                    return
                }
            }
        }

        if (chapters.isEmpty()) {
            _state.update { it.copy(isLoading = false, failure = FailureReason.ParseError) }
            return
        }

        val startIndex = requestedChapterId
            ?.let { id -> chapters.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: chapters.indexOfFirst { !it.isRead }.takeIf { it >= 0 }
            ?: 0

        _state.update { it.copy(workTitle = title, chapters = chapters) }
        openChapter(startIndex)
    }

    private fun openChapter(index: Int) {
        val chapters = _state.value.chapters
        val chapter = chapters.getOrNull(index) ?: return

        val sameChapter = _state.value.chapterId == chapter.id
        if (!sameChapter) saveHistory(keepSession = false)

        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, failure = null) }

        loadJob = viewModelScope.launch {
            when (val result = chapterRepository.getChapter(workId, chapter.id)) {
                is RepoResult.Ok -> {
                    // A bookmark carries its own position; every other way in restores progress.
                    val bookmarkProgress = requestedProgress
                        .takeIf { it in 0f..1f && chapter.id == requestedChapterId }
                    val restore = bookmarkProgress ?: chapter.progress
                    if (bookmarkProgress != null) requestedProgress = -1f
                    _state.update {
                        it.copy(
                            isLoading = false,
                            failure = null,
                            index = index,
                            chapterId = chapter.id,
                            chapterTitle = chapter.title.ifBlank { "Chapter ${chapter.number}" },
                            content = result.value,
                            initialProgress = restore,
                            progress = restore,
                            finished = restore >= it.settings.markReadThreshold,
                        )
                    }
                    if (!sameChapter || historyEntryId == null) beginHistory(chapter.id)
                    prefetchNext(index)
                }
                is RepoResult.Failed -> {
                    // PRD 11: parse and network failures leave a local trace for debugging.
                    Log.w("GoonReader", "chapter ${chapter.id} failed: ${result.reason}")
                    _state.update {
                        it.copy(isLoading = false, failure = result.reason, content = null)
                    }
                }
            }
        }
    }

    /** Bookmarks the current position; tapping again on the same spot removes it (PRD 10.9). */
    fun toggleBookmark() {
        val chapter = _state.value.currentChapter ?: return
        val progress = _state.value.progress
        viewModelScope.launch {
            val existing = bookmarkRepository.findNear(bookmarks.value, chapter.id, progress)
            if (existing != null) {
                bookmarkRepository.delete(existing.id)
            } else {
                bookmarkRepository.add(
                    workId = workId,
                    chapterId = chapter.id,
                    chapterNumber = chapter.number,
                    chapterTitle = chapter.title.ifBlank { "Chapter ${chapter.number}" },
                    progress = progress,
                )
            }
        }
    }

    /** Starts read-aloud here, or toggles it when this work is already being read (PRD 10.7). */
    fun startTts() {
        val current = _state.value
        if (current.currentChapter == null) return
        val playback = tts.state.value
        if (playback.isActive && playback.workId == workId) {
            tts.toggle()
            return
        }
        tts.start(
            workId = workId,
            workTitle = current.workTitle,
            chapters = current.chapters.map {
                TtsChapter(id = it.id, label = it.title.ifBlank { "Chapter ${it.number}" })
            },
            startIndex = current.index,
            startAt = current.progress,
        )
    }

    fun warmUpTts() = tts.warmUp()

    fun toggleTts() = tts.toggle()

    fun nextTtsParagraph() = tts.nextParagraph()

    fun previousTtsParagraph() = tts.previousParagraph()

    fun stopTts() = tts.stop()

    fun setTtsSleep(minutes: Int?) = tts.setSleep(minutes)

    /** Starts a history session. Preview works have no row to point at, so they are skipped. */
    private fun beginHistory(chapterId: String) {
        if (!workInLibrary || appSettings.incognito) return
        historyOpenedAt = System.currentTimeMillis()
        historyMillis = 0L
        viewModelScope.launch {
            historyEntryId = historyRepository.start(workId, chapterId)
        }
    }

    /** Writes the accumulated duration; [keepSession] is false when the chapter is being left. */
    private fun saveHistory(keepSession: Boolean) {
        val id = historyEntryId ?: return
        val now = System.currentTimeMillis()
        historyMillis += (now - historyOpenedAt).coerceAtLeast(0L)
        historyOpenedAt = now
        if (!keepSession) historyEntryId = null
        val duration = historyMillis
        viewModelScope.launch { historyRepository.addReadingTime(id, duration) }
    }

    /** PRD 7.3: warm exactly the next chapter, nothing more. */
    private fun prefetchNext(index: Int) {
        val next = _state.value.chapters.getOrNull(index + 1) ?: return
        viewModelScope.launch { chapterRepository.prefetch(workId, next.id) }
    }
}

private fun ChapterEntity.toReaderChapter() = ReaderChapter(
    id = id,
    number = number,
    title = title,
    isRead = isRead,
    progress = progress,
)
