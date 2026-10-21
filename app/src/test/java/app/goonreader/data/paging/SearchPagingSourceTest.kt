package app.goonreader.data.paging

import androidx.paging.PagingSource
import app.goonreader.data.repo.FailureReason
import app.goonreader.data.repo.RepoResult
import app.goonreader.data.repo.SearchPage
import app.goonreader.domain.model.Work
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPagingSourceTest {

    @Test
    fun `a refresh starts at page one and offers the next`() = runTest {
        val source = SearchPagingSource { page ->
            assertEquals(1, page)
            RepoResult.Ok(SearchPage(works(20), hasNext = true))
        }

        val result = source.load(refresh()).page()

        assertEquals(20, result.data.size)
        assertNull(result.prevKey)
        assertEquals(2, result.nextKey)
    }

    @Test
    fun `an appended page keeps its neighbours`() = runTest {
        val source = SearchPagingSource { page ->
            assertEquals(3, page)
            RepoResult.Ok(SearchPage(works(3), hasNext = false))
        }

        val result = source.load(
            PagingSource.LoadParams.Append<Int>(key = 3, loadSize = 20, placeholdersEnabled = false),
        ).page()

        assertEquals(2, result.prevKey)
        assertNull(result.nextKey)
    }

    @Test
    fun `a failure becomes a load error with its reason`() = runTest {
        val source = SearchPagingSource { RepoResult.Failed(FailureReason.Offline) }

        val result = source.load(refresh())

        assertTrue(result is PagingSource.LoadResult.Error)
        val error = (result as PagingSource.LoadResult.Error).throwable as SearchLoadException
        assertEquals(FailureReason.Offline, error.reason)
    }

    private fun refresh() = PagingSource.LoadParams.Refresh<Int>(
        key = null,
        loadSize = 20,
        placeholdersEnabled = false,
    )

    private fun PagingSource.LoadResult<Int, Work>.page(): PagingSource.LoadResult.Page<Int, Work> =
        this as PagingSource.LoadResult.Page

    private fun works(count: Int) = List(count) { Work(id = "w$it", title = "Work $it") }
}
