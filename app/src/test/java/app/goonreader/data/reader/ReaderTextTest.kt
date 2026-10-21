package app.goonreader.data.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextTest {

    @Test
    fun `each text block is numbered in document order`() {
        val body = ReaderText.annotate("<p>One</p><p>Two <em>and</em> three</p><p>Four</p>")

        assertEquals(listOf("One", "Two and three", "Four"), body.paragraphs)
        assertTrue(body.html.contains("""<p data-p="0">One</p>"""))
        assertTrue(body.html.contains("""<p data-p="1">Two <em>and</em> three</p>"""))
        assertTrue(body.html.contains("""<p data-p="2">Four</p>"""))
    }

    @Test
    fun `blocks without text are skipped but do not shift the numbering`() {
        val body = ReaderText.annotate("""<p>Before</p><p> </p><hr><p>After</p>""")

        assertEquals(listOf("Before", "After"), body.paragraphs)
        assertTrue(body.html.contains("""data-p="0""""))
        assertTrue(body.html.contains("""data-p="1""""))
        assertTrue(!body.html.contains("""data-p="2""""))
    }

    @Test
    fun `bare text still becomes one readable paragraph`() {
        val body = ReaderText.annotate("Just a line<br>and another")

        assertEquals(1, body.paragraphs.size)
        assertEquals("Just a line and another", body.paragraphs.single())
        assertTrue(body.html.contains("""data-p="0""""))
    }

    @Test
    fun `an empty body has nothing to read`() {
        val body = ReaderText.annotate("")

        assertTrue(body.paragraphs.isEmpty())
        assertEquals("", body.html)
    }
}
