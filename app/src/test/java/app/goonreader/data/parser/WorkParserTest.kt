package app.goonreader.data.parser

import app.goonreader.Fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkParserTest {

    private val work by lazy {
        checkNotNull(WorkParser.parse(Fixture.load("05-work-chapter1.html"), "4701869"))
    }

    @Test
    fun `reads title and author`() {
        assertEquals("Oh God Not Again!", work.title)
        assertEquals(listOf("Sarah1281"), work.authors)
    }

    @Test
    fun `reads every meta group`() {
        assertEquals("General Audiences", work.rating)
        assertEquals(listOf("No Archive Warnings Apply"), work.warnings)
        assertEquals(listOf("Gen"), work.categories)
        assertEquals(listOf("Harry Potter - J. K. Rowling"), work.fandoms)
        assertTrue(work.relationships.isNotEmpty())
        assertTrue(work.characters.contains("Harry Potter"))
        assertTrue(work.tags.isNotEmpty())
    }

    @Test
    fun `reads stats and completion`() {
        assertEquals(151_873L, work.wordCount)
        assertEquals(50, work.chaptersPublished)
        assertEquals(50, work.chaptersTotal)
        assertEquals(86_446, work.kudos)
        assertEquals(3_780_207L, work.hits)
        assertEquals(21_571, work.bookmarks)
        assertEquals(9_270, work.commentCount)
        assertTrue(work.isComplete)
    }

    @Test
    fun `reads dates from the status row`() {
        assertEquals(LocalDate.of(2015, 9, 1), work.publishedAt)
        assertEquals(LocalDate.of(2015, 9, 1), work.updatedAt)
    }

    @Test
    fun `reads summary and work notes`() {
        assertTrue(work.summaryHtml.orEmpty().contains("Veil"))
        assertNotNull(work.notesHtml)
        assertTrue(work.notesHtml.orEmpty().contains("Italiano"))
    }

    @Test
    fun `returns null when the page has no work`() {
        assertEquals(null, WorkParser.parse(Fixture.load("11-work-not-found.html"), "1"))
    }
}
