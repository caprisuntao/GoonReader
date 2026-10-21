package app.goonreader.data.parser

import app.goonreader.Fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChapterListParserTest {

    private val chapters by lazy {
        ChapterListParser.parse(Fixture.load("06-work-navigate.html"), "4701869")
    }

    @Test
    fun `parses all fifty chapters in order`() {
        assertEquals(50, chapters.size)
        assertEquals((1..50).toList(), chapters.map { it.number })
        assertEquals(50, chapters.map { it.id }.distinct().size)
    }

    @Test
    fun `reads id, title and date of the first chapter`() {
        val first = chapters.first()
        assertEquals("10736366", first.id)
        assertEquals("4701869", first.workId)
        assertEquals("Chapter 1", first.title)
        assertEquals(LocalDate.of(2015, 9, 1), first.publishedAt)
    }

    @Test
    fun `single-chapter work has no chapter index`() {
        // The one-shot fixture has no /navigate page; the empty list is the signal to
        // fall back to the work page rather than an error.
        val none = ChapterListParser.parse(Fixture.load("12-work-single-chapter.html"), "999")
        assertTrue(none.isEmpty())
    }
}
