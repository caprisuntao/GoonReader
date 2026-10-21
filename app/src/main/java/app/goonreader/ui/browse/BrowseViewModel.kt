package app.goonreader.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.goonreader.data.db.WorkDao
import app.goonreader.data.paging.SearchPagingSource
import app.goonreader.data.prefs.AppPreferences
import app.goonreader.data.repo.FailureReason
import app.goonreader.data.repo.RepoResult
import app.goonreader.data.repo.SearchPage
import app.goonreader.data.repo.SearchRepository
import app.goonreader.domain.model.SearchFilters
import app.goonreader.domain.model.SortColumn
import app.goonreader.domain.model.SortDirection
import app.goonreader.domain.model.TagRef
import app.goonreader.domain.model.TagType
import app.goonreader.domain.model.Work
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which of Browse's three panes is showing. */
sealed interface BrowsePage {
    data object Home : BrowsePage
    data class Results(val title: String, val fromTagPicker: Boolean = false) : BrowsePage
    data object Tags : BrowsePage
}

data class BrowseUiState(
    val page: BrowsePage = BrowsePage.Home,
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    /** Bumped per search so the results list can start at the top instead of inheriting a scroll. */
    val searchId: Int = 0,
    val tagQuery: String = "",
    val tagType: TagType = TagType.Any,
    /** Null until a tag search has run, so "no results" can be told apart from "not searched". */
    val tagResults: List<TagRef>? = null,
    val tagLoading: Boolean = false,
    val tagFailure: FailureReason? = null,
)

/**
 * Browse orchestration: the search bar, the filter sheet, the tag picker, and one Paging flow
 * that is rebuilt whenever the active query changes.
 *
 * Nothing here touches the library: results only *read* which work ids are already in it.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: SearchRepository,
    workDao: WorkDao,
    preferences: AppPreferences,
) : ViewModel() {

    /** Exactly what the pager should fetch; null means "no results pane is open". */
    private sealed interface Request {
        data class Works(val filters: SearchFilters) : Request
        data class Tag(val href: String, val sort: SortColumn) : Request
    }

    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    val libraryIds: StateFlow<Set<String>> = workDao.observeLibraryIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** PRD 9.7: Downloaded only also keeps Browse offline. */
    val downloadedOnly: StateFlow<Boolean> = preferences.settings
        .map { it.downloadedOnly }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val request = MutableStateFlow<Request?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: Flow<PagingData<Work>> = request
        .filterNotNull()
        .flatMapLatest { active ->
            Pager(
                PagingConfig(
                    pageSize = SearchRepository.RESULTS_PER_PAGE,
                    initialLoadSize = SearchRepository.RESULTS_PER_PAGE,
                    // AO3 allows one request every couple of seconds; prefetching one page ahead
                    // is enough to keep scrolling smooth without queueing requests.
                    prefetchDistance = 3,
                    enablePlaceholders = false,
                ),
            ) {
                SearchPagingSource { page -> fetch(active, page) }
            }.flow
        }
        .cachedIn(viewModelScope)

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    /** Search runs on submit only: AO3 must never see a request per keystroke (PRD 7.3). */
    fun submitQuery() {
        val current = _state.value
        startSearch(current.filters.copy(query = current.query.trim()))
    }

    fun applyFilters(filters: SearchFilters) = startSearch(filters)

    fun openLatest() = startSearch(SearchFilters(sortColumn = SortColumn.Updated), title = "Latest")

    fun openMostKudos() = startSearch(SearchFilters(sortColumn = SortColumn.Kudos), title = "Most kudos")

    fun openTagPicker() = _state.update { it.copy(page = BrowsePage.Tags) }

    /**
     * Back out one pane. A tag's results return to the tag list (so more tags can be opened);
     * anything else returns to the home entries.
     */
    fun back() {
        val page = _state.value.page
        request.value = null
        _state.update {
            when {
                page is BrowsePage.Results && page.fromTagPicker -> it.copy(page = BrowsePage.Tags)
                else -> it.copy(page = BrowsePage.Home, tagResults = null, tagFailure = null)
            }
        }
    }

    fun onTagQueryChange(query: String) = _state.update { it.copy(tagQuery = query) }

    fun onTagTypeChange(type: TagType) = _state.update { it.copy(tagType = type) }

    fun submitTagSearch() {
        val current = _state.value
        if (current.tagQuery.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(tagLoading = true, tagFailure = null) }
            val type = current.tagType.takeIf { it != TagType.Any }
            when (val result = repository.searchTags(current.tagQuery.trim(), type)) {
                is RepoResult.Ok -> _state.update {
                    it.copy(tagLoading = false, tagResults = result.value)
                }
                is RepoResult.Failed -> _state.update {
                    it.copy(tagLoading = false, tagFailure = result.reason, tagResults = null)
                }
            }
        }
    }

    fun openTag(tag: TagRef) {
        _state.update {
            it.copy(
                page = BrowsePage.Results(tag.name, fromTagPicker = true),
                searchId = it.searchId + 1,
            )
        }
        request.value = Request.Tag(tag.href, SortColumn.Updated)
    }

    private fun startSearch(filters: SearchFilters, title: String = titleFor(filters)) {
        _state.update {
            it.copy(
                page = BrowsePage.Results(title),
                filters = filters,
                query = filters.query,
                searchId = it.searchId + 1,
            )
        }
        request.value = Request.Works(filters)
    }

    private suspend fun fetch(active: Request, page: Int): RepoResult<SearchPage> = when (active) {
        is Request.Works -> repository.search(active.filters, page)
        is Request.Tag -> repository.tagWorks(
            tagHref = active.href,
            sortColumn = active.sort,
            sortDirection = SortDirection.Descending,
            page = page,
        )
    }

    private fun titleFor(filters: SearchFilters): String = when {
        filters.query.isNotBlank() -> "\"${filters.query}\""
        filters.hasFilters -> "Filtered search"
        else -> "All works"
    }
}
