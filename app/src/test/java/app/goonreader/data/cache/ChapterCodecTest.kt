package app.goonreader.data.cache

import app.goonreader.domain.model.ChapterContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterCodecTest {

    private val content = ChapterContent(
        workId = "4701869",
        chapterId = "10736366",
        title = "Chapter 1",
        summaryHtml = "<p>Summary &amp; more</p>",
        startNotesHtml = "<p>Start note</p>",
        endNotesHtml = "<p>End note</p>",
        bodyHtml = "<p>Body <em>text</em></p>",
    )

    @Test
    fun `a chapter survives encode and decode`() {
        val encoded = ChapterCodec.encode(content)
        assertEquals(content, ChapterCodec.decode(encoded, "4701869", "10736366"))
    }

    @Test
    fun `missing optional parts stay missing`() {
        val bare = ChapterContent("1", "2", title = null, summaryHtml = null, startNotesHtml = null, endNotesHtml = null, bodyHtml = "<p>only text</p>")
        assertEquals(bare, ChapterCodec.decode(ChapterCodec.encode(bare), "1", "2"))
    }

    @Test
    fun `html entities in the body are preserved`() {
        val encoded = ChapterCodec.encode(content.copy(bodyHtml = "<p>R&amp;D &lt;3</p>"))
        assertEquals("<p>R&amp;D &lt;3</p>", ChapterCodec.decode(encoded, "4701869", "10736366")?.bodyHtml)
    }

    @Test
    fun `a document without a chapter body is rejected`() {
        assertNull(ChapterCodec.decode("<html><body><p>not a chapter</p></body></html>", "1", "2"))
    }
}
