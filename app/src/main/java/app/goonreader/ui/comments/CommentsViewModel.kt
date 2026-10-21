package app.goonreader.ui.comments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.goonreader.data.repo.CommentRepository
import app.goonreader.data.repo.FailureReason
import app.goonreader.data.repo.RepoResult
import app.goonreader.domain.model.Comment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentsUiState(
    val comments: List<Comment> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val failure: FailureReason? = null,
    val hasMore: Boolean = false,
)

/**
 * One comments page at a time (PRD 7.3/9.10): the first page on open, the next only when the
 * user asks. Comments are never prefetched, cached or written to the database.
 */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val repository: CommentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val workId: String = checkNotNull(savedStateHandle.get<String>("workId")) {
        "CommentsViewModel requires a workId route argument"
    }
    val chapterId: String = checkNotNull(savedStateHandle.get<String>("chapterId")) {
        "CommentsViewModel requires a chapterId route argument"
    }

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private var nextPage: Int? = 1

    init {
        load()
    }

    fun loadMore() {
        if (_state.value.loading || _state.value.loadingMore) return
        load()
    }

    fun retry() = load()

    private fun load() {
        val page = nextPage ?: return
        val firstPage = _state.value.comments.isEmpty()
        _state.update { it.copy(loading = firstPage, loadingMore = !firstPage, failure = null) }

        viewModelScope.launch {
            when (val result = repository.load(workId, chapterId, page)) {
                is RepoResult.Ok -> {
                    val body = result.value
                    nextPage = body.nextPage
                    _state.update {
                        it.copy(
                            comments = it.comments + body.comments,
                            total = body.total ?: it.total,
                            loading = false,
                            loadingMore = false,
                            failure = null,
                            hasMore = body.nextPage != null,
                        )
                    }
                }
                is RepoResult.Failed -> _state.update {
                    it.copy(loading = false, loadingMore = false, failure = result.reason)
                }
            }
        }
    }
}
