package app.goonreader.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.goonreader.data.repo.FailureReason
import app.goonreader.data.repo.RepoResult
import app.goonreader.data.repo.SearchPage
import app.goonreader.domain.model.Work

/** Carries the repository's [FailureReason] through Paging so the UI can explain itself. */
class SearchLoadException(val reason: FailureReason) : Exception("search failed: $reason")

/**
 * Paging adapter over one AO3 results query. The fetch is injected so the same source serves
 * both work searches and tag pages without a request model.
 */
class SearchPagingSource(
    private val fetch: suspend (page: Int) -> RepoResult<SearchPage>,
) : PagingSource<Int, Work>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Work> {
        val page = params.key ?: FIRST_PAGE
        return when (val result = fetch(page)) {
            is RepoResult.Ok -> LoadResult.Page(
                data = result.value.works,
                prevKey = if (page == FIRST_PAGE) null else page - 1,
                nextKey = if (result.value.hasNext) page + 1 else null,
            )
            is RepoResult.Failed -> LoadResult.Error(SearchLoadException(result.reason))
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Work>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
