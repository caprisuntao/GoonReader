package app.goonreader.data.network

import app.goonreader.domain.model.Category
import app.goonreader.domain.model.Completion
import app.goonreader.domain.model.Crossovers
import app.goonreader.domain.model.Rating
import app.goonreader.domain.model.SearchFilters
import app.goonreader.domain.model.SortColumn
import app.goonreader.domain.model.SortDirection
import app.goonreader.domain.model.TagType
import app.goonreader.domain.model.UpdatedWithin
import app.goonreader.domain.model.Warning
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ao3UrlsTest {

    @Test
    fun `builds work, chapter and navigate urls`() {
        assertEquals("https://archiveofourown.org/works/4701869?view_adult=true", Ao3Urls.work("4701869"))
        assertEquals(
            "https://archiveofourown.org/works/4701869/chapters/10736366?view_adult=true",
            Ao3Urls.chapter("4701869", "10736366"),
        )
        // The chapter index needs no consent parameter: it renders no work content.
        assertEquals("https://archiveofourown.org/works/4701869/navigate", Ao3Urls.navigate("4701869"))
    }

    @Test
    fun `search uses ao3 parameter names`() {
        val url = Ao3Urls.search(
            SearchFilters(query = "time travel", sortColumn = SortColumn.Kudos),
            page = 2,
        ).toHttpUrl()

        assertEquals("time travel", url.queryParameter("work_search[query]"))
        assertEquals("kudos_count", url.queryParameter("work_search[sort_column]"))
        assertEquals("desc", url.queryParameter("work_search[sort_direction]"))
        assertEquals("2", url.queryParameter("page"))
    }

    @Test
    fun `unset filters are left out of the url`() {
        val url = Ao3Urls.search(SearchFilters(query = "anything")).toHttpUrl()

        assertEquals("anything", url.queryParameter("work_search[query]"))
        assertNull(url.queryParameter("page"))
        assertNull(url.queryParameter("work_search[rating_ids][]"))
        assertNull(url.queryParameter("work_search[sort_column]"))
        assertNull(url.queryParameter("work_search[complete]"))
        assertNull(url.queryParameter("work_search[word_count]"))
    }

    @Test
    fun `filter chips become ao3 ids`() {
        val url = Ao3Urls.search(
            SearchFilters(
                rating = Rating.TeenAndUp,
                warnings = setOf(Warning.MajorCharacterDeath, Warning.NoArchiveWarningsApply),
                categories = setOf(Category.Mm),
            ),
        ).toHttpUrl()

        assertEquals("11", url.queryParameter("work_search[rating_ids][]"))
        assertEquals(
            listOf("18", "16"),
            url.queryParameterValues("work_search[archive_warning_ids][]"),
        )
        assertEquals(listOf("23"), url.queryParameterValues("work_search[category_ids][]"))
    }

    @Test
    fun `completion crossover and single chapter map to ao3 values`() {
        val url = Ao3Urls.search(
            SearchFilters(
                completion = Completion.InProgress,
                crossovers = Crossovers.Only,
                singleChapterOnly = true,
            ),
        ).toHttpUrl()

        assertEquals("F", url.queryParameter("work_search[complete]"))
        assertEquals("T", url.queryParameter("work_search[crossover]"))
        assertEquals("1", url.queryParameter("work_search[single_chapter]"))
    }

    @Test
    fun `word count uses ao3 range syntax`() {
        val bounded = Ao3Urls.search(SearchFilters(wordsFrom = 1_000, wordsTo = 5_000)).toHttpUrl()
        assertEquals("1000-5000", bounded.queryParameter("work_search[word_count]"))

        val atLeast = Ao3Urls.search(SearchFilters(wordsFrom = 1_000)).toHttpUrl()
        assertEquals(">1000", atLeast.queryParameter("work_search[word_count]"))

        val atMost = Ao3Urls.search(SearchFilters(wordsTo = 5_000)).toHttpUrl()
        assertEquals("<5000", atMost.queryParameter("work_search[word_count]"))
    }

    @Test
    fun `updated filter uses ao3 relative date syntax`() {
        val url = Ao3Urls.search(SearchFilters(updatedWithin = UpdatedWithin.Week)).toHttpUrl()
        assertEquals("<7 days", url.queryParameter("work_search[revised_at]"))
    }

    @Test
    fun `tag works url keeps the encoded href and appends works`() {
        // AO3 escapes "." as *d* inside tag names; re-encoding it produces a 404.
        val href = "/tags/Harry%20Potter%20-%20J*d*%20K*d*%20Rowling"
        val url = Ao3Urls.tagWorks(href, SortColumn.Updated, SortDirection.Descending, page = 2).toHttpUrl()

        assertEquals(
            "/tags/Harry%20Potter%20-%20J*d*%20K*d*%20Rowling/works",
            url.encodedPath,
        )
        assertEquals("revised_at", url.queryParameter("work_search[sort_column]"))
        assertEquals("desc", url.queryParameter("work_search[sort_direction]"))
        assertEquals("2", url.queryParameter("page"))
    }

    @Test
    fun `tag search sends the typed name and type`() {
        val url = Ao3Urls.tagSearch("time travel", TagType.Freeform).toHttpUrl()

        assertEquals("time travel", url.queryParameter("tag_search[name]"))
        assertEquals("Freeform", url.queryParameter("tag_search[type]"))
        assertEquals("/tags/search", url.encodedPath)
    }

    @Test
    fun `tag hrefs resolve without re-encoding the asterisk escapes`() {
        // AO3 escapes "/" as *s* and "&" as *a* inside tag names; re-encoding would break them.
        val href = "/tags/Harry%20Potter%20-%20J*d*%20K*d*%20Rowling/works"
        assertEquals("https://archiveofourown.org$href", Ao3Urls.resolve(href))
    }

    @Test
    fun `absolute hrefs are left alone`() {
        val href = "https://archiveofourown.org/works/4701869/chapters/10736366?view_adult=true"
        assertEquals(href, Ao3Urls.resolve(href))
    }

    @Test
    fun `tag works url does not double the works suffix`() {
        val url = Ao3Urls.tagWorks("/tags/Time%20Travel/works").toHttpUrl()
        assertTrue(url.encodedPath.endsWith("/tags/Time%20Travel/works"))
    }
}
