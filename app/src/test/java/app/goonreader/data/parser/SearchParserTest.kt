package app.goonreader.data.parser

import app.goonreader.Fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SearchParserTest {

    private val page by lazy { SearchParser.parse(Fixture.load("01-search-results.html")) }
    private val first by lazy { page.first() }

    @Test
    fun `parses every blurb on the page`() {
        assertEquals(20, page.size)
        assertEquals(page.size, page.map { it.id }.distinct().size)
    }

    @Test
    fun `reads id, title and author`() {
        assertEquals("4701869", first.id)
        assertEquals("Oh God Not Again!", first.title)
        assertEquals(listOf("Sarah1281"), first.authors)
        assertEquals(listOf("Harry Potter - J. K. Rowling"), first.fandoms)
    }

    @Test
    fun `reads rating, warnings, categories and completion`() {
        assertEquals("General Audiences", first.rating)
        assertEquals(listOf("No Archive Warnings Apply"), first.warnings)
        assertEquals(listOf("Gen"), first.categories)
        assertTrue(first.isComplete)
    }

    @Test
    fun `reads stats without thousands separators`() {
        assertEquals(151_873L, first.wordCount)
        assertEquals(50, first.chaptersPublished)
        assertEquals(50, first.chaptersTotal)
        assertEquals(9_270, first.commentCount)
        assertEquals(86_446, first.kudos)
        assertEquals("English", first.language)
        assertNotNull(first.hits)
    }

    @Test
    fun `reads updated date`() {
        assertEquals(LocalDate.of(2015, 9, 1), first.updatedAt)
    }

    @Test
    fun `separates relationships, characters and freeform tags`() {
        assertTrue(first.relationships.contains("Draco Malfoy & Harry Potter"))
        assertTrue(first.characters.contains("Harry Potter"))
        assertTrue(first.tags.isNotEmpty())
        // Groups must not bleed into each other.
        assertTrue(first.tags.none { it in first.characters })
    }

    @Test
    fun `keeps summary html`() {
        assertTrue(first.summaryHtml.orEmpty().contains("Veil"))
    }

    @Test
    fun `empty result page parses to nothing`() {
        assertTrue(SearchParser.parse(Fixture.load("03-search-results-empty.html")).isEmpty())
    }

    @Test
    fun `second page yields different works`() {
        val page2 = SearchParser.parse(Fixture.load("02-search-results-page2.html"))
        assertTrue(page2.isNotEmpty())
        val firstPageIds = page.map { it.id }.toSet()
        assertTrue(page2.none { it.id in firstPageIds })
    }
}
