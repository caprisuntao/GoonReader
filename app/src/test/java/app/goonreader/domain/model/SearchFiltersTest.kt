package app.goonreader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFiltersTest {

    @Test
    fun `a default filter set narrows nothing`() {
        assertTrue(SearchFilters().isUnfiltered)
        assertEquals(0, SearchFilters().filterCount)
        assertTrue(SearchFilters(sortColumn = SortColumn.Kudos).isUnfiltered)
    }

    @Test
    fun `search terms and ids count as filters`() {
        assertFalse(SearchFilters(query = "time travel").isUnfiltered)
        // The search terms have their own field; the badge only counts sheet filters.
        assertFalse(SearchFilters(query = "time travel").hasFilters)
        assertEquals(3, SearchFilters(rating = Rating.Mature, warnings = setOf(Warning.RapeNonCon, Warning.Underage)).filterCount)
        assertEquals(1, SearchFilters(languageId = "en").filterCount)
        assertEquals(1, SearchFilters(wordsFrom = 100).filterCount)
    }

    @Test
    fun `word count param covers every range shape`() {
        assertEquals("1000-5000", SearchFilters(wordsFrom = 1000, wordsTo = 5000).wordCountParam())
        assertEquals("1000-5000", SearchFilters(wordsFrom = 5000, wordsTo = 1000).wordCountParam())
        assertEquals(">1000", SearchFilters(wordsFrom = 1000).wordCountParam())
        assertEquals("<5000", SearchFilters(wordsTo = 5000).wordCountParam())
        assertNull(SearchFilters().wordCountParam())
    }

    @Test
    fun `clearing keeps the search terms and the sort`() {
        val filters = SearchFilters(
            query = "time travel",
            title = "a title",
            rating = Rating.Explicit,
            sortColumn = SortColumn.Hits,
        )

        val cleared = filters.cleared()

        assertEquals("time travel", cleared.query)
        assertEquals(SortColumn.Hits, cleared.sortColumn)
        assertEquals(0, cleared.filterCount)
        assertFalse(cleared.hasFilters)
    }
}
