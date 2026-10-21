package app.goonreader.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.goonreader.data.db.CategoryRow
import app.goonreader.data.download.DownloadManager
import app.goonreader.data.download.DownloadState
import app.goonreader.data.prefs.AppPreferences
import app.goonreader.data.repo.CategoryRepository
import app.goonreader.data.repo.FailureReason
import app.goonreader.data.repo.RepoResult
import app.goonreader.data.repo.WorkBundle
import app.goonreader.data.repo.WorkRepository
import app.goonreader.data.repo.toDomain
import app.goonreader.data.repo.toLocalDate
import app.goonreader.domain.model.Work
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ChapterRow(
    val id: String,
    val number: Int,
    val title: String,
    val publishedAt: LocalDate?,
    val isRead: Boolean,
    val isDownloaded: Boolean = false,
)

data class WorkDetailsUiState(
    val isLoading: Boolean = true,
    val work: Work? = null,
    val chapters: List<ChapterRow> = emptyList(),
    val inLibrary: Boolean = false,
    val failure: FailureReason? = null,
)

/**
 * Work details reads from the database when the work is already stored and only goes to the
 * network for a preview. Previewing never writes (PRD 4); "Add to library" persists the bundle
 * that was parsed when the screen opened, so it does not fetch a second time.
 */
@HiltViewModel
class WorkDetailsViewModel @Inject constructor(
    private val repository: WorkRepository,
    private val downloads: DownloadManager,
    private val categories: CategoryRepository,
    preferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val workId: String = checkNotNull(savedStateHandle.get<String>("workId")) {
        "WorkDetailsViewModel requires a workId route argument"
    }

    private val _state = MutableStateFlow(WorkDetailsUiState())
    val state: StateFlow<WorkDetailsUiState> = _state.asStateFlow()

    val downloadState: StateFlow<DownloadState> = downloads.state

    /** All categories, and which ones this work is in (PRD 9.2 categories). */
    val allCategories: StateFlow<List<CategoryRow>> = categories.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val assignedCategoryIds: StateFlow<Set<Long>> = categories.observeIdsFor(workId)
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** PRD 9.8 setting; the menu label and the "Next N" action both use it. */
    val downloadAhead: StateFlow<Int> = preferences.settings
        .map { it.downloadAhead }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)

    private var preview: WorkBundle? = null

    init {
        viewModelScope.launch {
            if (repository.getWork(workId) != null) showStored() else fetchPreview()
        }
        // A batch writes isDownloaded as it goes; re-read the chapter list when it stops so the
        // per-chapter ticks appear without the user leaving the screen.
        viewModelScope.launch {
            var wasRunning = false
            downloads.state.collect { download ->
                if (wasRunning && !download.running && download.workId == workId && _state.value.inLibrary) {
                    showStored()
                }
                wasRunning = download.running
            }
        }
    }

    fun addToLibrary() = viewModelScope.launch {
        // Normally the bundle parsed on open. After "Remove" that memory is gone, so a re-add
        // fetches once instead of doing nothing.
        val bundle = preview ?: when (val fetched = repository.fetchWork(workId)) {
            is RepoResult.Ok -> fetched.value
            is RepoResult.Failed -> {
                _state.update { it.copy(failure = fetched.reason) }
                return@launch
            }
        }
        repository.addToLibrary(bundle)
        preview = null
        showStored()
    }

    fun removeFromLibrary() = viewModelScope.launch {
        // A batch for this work would keep writing files after its rows are gone.
        if (downloads.state.value.workId == workId) downloads.cancel()
        repository.removeFromLibrary(workId)
        _state.update { it.copy(inLibrary = false) }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, failure = null) }
        if (_state.value.inLibrary) {
            when (val result = repository.refresh(workId)) {
                is RepoResult.Ok -> showStored()
                is RepoResult.Failed -> _state.update { it.copy(isLoading = false, failure = result.reason) }
            }
        } else {
            fetchPreview()
        }
    }

    fun retry() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, failure = null) }
        if (repository.getWork(workId) != null) showStored() else fetchPreview()
    }

    /** Re-reads stored state, e.g. after the reader marked a chapter as read. */
    fun reload() = viewModelScope.launch {
        if (!_state.value.isLoading && repository.getWork(workId) != null) showStored()
    }

    /** Download menu (PRD 9.6). Only chapters that are not already stored are queued. */
    fun downloadAll() = enqueuePending { it }

    fun downloadUnread() = enqueuePending { it.filter { row -> !row.isRead } }

    fun downloadNext(count: Int) = enqueuePending { it.take(count) }

    fun cancelDownloads() = downloads.cancel()

    fun retryDownloads() = downloads.retryFailed()

    fun deleteDownloads() = viewModelScope.launch {
        downloads.deleteDownloads(workId, _state.value.chapters.filter { it.isDownloaded }.map { it.id })
        // The flags just changed; re-read so the ticks disappear without leaving the screen.
        showStored()
    }

    fun toggleCategory(categoryId: Long, assigned: Boolean) = viewModelScope.launch {
        categories.setAssigned(workId, categoryId, assigned)
    }

    private fun enqueuePending(select: (List<ChapterRow>) -> List<ChapterRow>) {
        val pending = select(_state.value.chapters.filter { !it.isDownloaded })
        downloads.enqueue(workId, pending.map { it.id })
    }

    private suspend fun showStored() {
        val stored = repository.getWork(workId)
        if (stored == null) {
            fetchPreview()
            return
        }
        _state.value = WorkDetailsUiState(
            isLoading = false,
            work = stored.toDomain(),
            chapters = repository.getChapters(workId).map { chapter ->
                ChapterRow(
                    id = chapter.id,
                    number = chapter.number,
                    title = chapter.title,
                    publishedAt = chapter.publishedAt?.toLocalDate(),
                    isRead = chapter.isRead,
                    isDownloaded = chapter.isDownloaded,
                )
            },
            inLibrary = stored.inLibrary,
        )
    }

    private suspend fun fetchPreview() {
        _state.update { it.copy(isLoading = true, failure = null) }
        when (val result = repository.fetchWork(workId)) {
            is RepoResult.Ok -> {
                preview = result.value
                _state.value = WorkDetailsUiState(
                    isLoading = false,
                    work = result.value.work,
                    chapters = result.value.chapters.map { chapter ->
                        ChapterRow(
                            id = chapter.id,
                            number = chapter.number,
                            title = chapter.title,
                            publishedAt = chapter.publishedAt,
                            isRead = false,
                        )
                    },
                    inLibrary = false,
                )
            }
            is RepoResult.Failed -> _state.update { it.copy(isLoading = false, failure = result.reason) }
        }
    }
}
