package app.goonreader.data.repo

import app.goonreader.Fixture
import app.goonreader.data.network.Ao3Client
import app.goonreader.data.network.Ao3Raw
import app.goonreader.data.network.Ao3Transport
import app.goonreader.data.network.RateLimiter
import app.goonreader.domain.model.SearchFilters
import app.goonreader.domain.model.SortColumn
import app.goonreader.domain.model.SortDirection
import app.goonreader.domain.model.TagType
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SearchRepositoryTest {

    @Test
    fun `a search page parses and reports more pages`() = runTest {
        val page = (repository(Fixture.load("01-search-results.html"))
            .search(SearchFilters(query = "time travel"), page = 1) as RepoResult.Ok).value

        assertEquals(20, page.works.size)
        assertTrue(page.hasNext)
        assertEquals("4701869", page.works.first().id)
    }

    @Test
    fun `an empty page ends the list`() = runTest {
        val page = (repository(Fixture.load("03-search-results-empty.html"))
            .search(SearchFilters(query = "zzqqxx"), page = 1) as RepoResult.Ok).value

        assertTrue(page.works.isEmpty())
        assertFalse(page.hasNext)
    }

    @Test
    fun `a search requests ao3's search endpoint with the filters`() = runTest {
        var requested = ""
        repository(Fixture.load("01-search-results.html")) { requested = it }
            .search(SearchFilters(query = "time travel", sortColumn = SortColumn.Kudos), page = 2)

        val url = requested.toHttpUrl()
        assertEquals("/works/search", url.encodedPath)
        assertEquals("time travel", url.queryParameter("work_search[query]"))
        assertEquals("kudos_count", url.queryParameter("work_search[sort_column]"))
        assertEquals("2", url.queryParameter("page"))
    }

    @Test
    fun `tag browse reads the tag's works page`() = runTest {
        var requested = ""
        val href = "/tags/Harry%20Potter%20-%20J*d*%20K*d*%20Rowling"
        val page = (repository(Fixture.load("15-tag-works.html")) { requested = it }
            .tagWorks(href, SortColumn.Kudos, SortDirection.Descending, page = 1) as RepoResult.Ok).value

        assertEquals(20, page.works.size)
        assertTrue(page.hasNext)
        val url = requested.toHttpUrl()
        assertEquals("$href/works", url.encodedPath)
        assertEquals("kudos_count", url.queryParameter("work_search[sort_column]"))
    }

    @Test
    fun `tag search reads the tag list`() = runTest {
        val tags = (repository(Fixture.load("14-tag-search.html"))
            .searchTags("time travel", TagType.Any) as RepoResult.Ok).value

        assertEquals(50, tags.size)
        assertEquals("!!TIME TRAVEL ALERT!!", tags.first().name)
    }

    @Test
    fun `a challenge is reported as a challenge`() = runTest {
        val client = Ao3Client(
            RateLimiter(),
            Ao3Transport { Ao3Raw(403, "<script src=\"/cdn-cgi/challenge-platform/x\"></script>") },
        )
        val result = SearchRepository(client).search(SearchFilters(query = "time travel"), page = 1)

        assertEquals(FailureReason.ChallengeRequired, (result as RepoResult.Failed).reason)
    }

    @Test
    fun `a dropped connection is reported as offline`() = runTest {
        val client = Ao3Client(RateLimiter(), Ao3Transport { throw IOException("no route to host") })
        val result = SearchRepository(client).search(SearchFilters(query = "time travel"), page = 1)

        assertEquals(FailureReason.Offline, (result as RepoResult.Failed).reason)
    }

    private fun repository(body: String, onRequest: (String) -> Unit = {}): SearchRepository {
        val client = Ao3Client(
            RateLimiter(),
            Ao3Transport { url ->
                onRequest(url)
                Ao3Raw(200, body)
            },
        )
        return SearchRepository(client)
    }
}
