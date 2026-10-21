package app.goonreader.data.parser

import app.goonreader.Fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** PRD 9.10 parser, against a real chapter page saved with its comments shown. */
class CommentParserTest {

    private val page by lazy { CommentParser.parse(Fixture.load("08-chapter-comments.html")) }

    @Test
    fun `reads the total and the next page`() {
        assertEquals(741, page.total)
        assertEquals(2, page.nextPage)
    }

    @Test
    fun `reads the first comment`() {
        val first = page.comments.first()

        assertEquals("38335292", first.id)
        assertEquals("Account Deleted", first.author)
        assertEquals(LocalDate.of(2015, 9, 2), first.postedAt)
        assertEquals(0, first.depth)
        assertTrue(first.bodyHtml.contains("YES"))
        assertFalse(first.bodyHtml.contains("<script"))
    }

    @Test
    fun `a reply is nested one level`() {
        val reply = page.comments.first { it.id == "38795114" }

        assertEquals("Sarah1281", reply.author)
        assertEquals(1, reply.depth)
        assertTrue(reply.bodyHtml.contains("reread"))
    }

    @Test
    fun `a top-level comment after a reply is back at depth zero`() {
        assertEquals(0, page.comments.first { it.id == "55678342" }.depth)
    }

    @Test
    fun `a page without comments shown is empty but still knows the count`() {
        val plain = CommentParser.parse(Fixture.load("05-work-chapter1.html"))

        assertTrue(plain.comments.isEmpty())
        assertEquals(741, plain.total)
        assertNull(plain.nextPage)
    }
}
