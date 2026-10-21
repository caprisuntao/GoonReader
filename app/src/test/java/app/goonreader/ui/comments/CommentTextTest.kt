package app.goonreader.ui.comments

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentTextTest {

    @Test
    fun `paragraphs survive as blank lines`() {
        assertEquals("one\n\ntwo", commentPlainText("<p>one</p><p>two</p>"))
    }

    @Test
    fun `inline markup is flattened to text`() {
        assertEquals("bold word", commentPlainText("<p><strong>bold</strong> word</p>"))
    }

    @Test
    fun `html without paragraphs still reads`() {
        assertEquals("plain text", commentPlainText("plain <em>text</em>"))
    }
}
