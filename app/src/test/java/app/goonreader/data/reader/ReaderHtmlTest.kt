package app.goonreader.data.reader

import app.goonreader.data.prefs.ReaderTheme
import app.goonreader.domain.model.ChapterContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHtmlTest {

    private val content = ChapterContent(
        workId = "1",
        chapterId = "2",
        title = "A & B <C>",
        summaryHtml = "<p>Summary text</p>",
        startNotesHtml = "<p>Start note</p>",
        endNotesHtml = "<p>End note</p>",
        bodyHtml = "<p>Body <em>text</em></p>",
    )

    private val appearance = ReaderAppearance(
        palette = ReaderTheme.Night.palette(),
        fontPx = 18f,
        lineSpacing = 1.5f,
        paragraphSpacingEm = 0.8f,
        sideMarginPx = 20,
        verticalMarginPx = 14,
        justify = false,
        showSummary = true,
        showNotes = true,
    )

    private fun render(appearance: ReaderAppearance = this.appearance, content: ChapterContent = this.content): String =
        ReaderHtml.render(content, appearance)

    @Test
    fun `the document is standalone and carries no script`() {
        val html = render()
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"))
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("onload"))
    }

    @Test
    fun `chapter text, notes and summary are rendered`() {
        val html = render()
        assertTrue(html.contains("""<p data-p="0">Body <em>text</em></p>"""))
        assertTrue(html.contains("<p>Start note</p>"))
        assertTrue(html.contains("<p>End note</p>"))
        assertTrue(html.contains("<p>Summary text</p>"))
    }

    @Test
    fun `paged mode lays the text out in viewport-width columns`() {
        val paged = render(appearance.copy(paged = true))
        assertTrue(paged.contains("""<article id="pages">"""))
        assertTrue(paged.contains("column-width:calc(100vw - 40px)"))
        assertTrue(paged.contains("column-fill:auto"))
        assertFalse(paged.contains("padding:14px 20px 48px;"))
        assertFalse(render().contains("id=\"pages\""))
    }

    @Test
    fun `the title is escaped, not injected`() {
        assertTrue(render().contains("<h1 class=\"chapter-title\">A &amp; B &lt;C&gt;</h1>"))
    }

    @Test
    fun `appearance drives font, spacing and margins`() {
        val html = render()
        assertTrue(html.contains("font-size:18.0px"))
        assertTrue(html.contains("line-height:1.5"))
        assertTrue(html.contains("padding:14px 20px 48px;"))
        assertTrue(html.contains("text-align:start"))
    }

    @Test
    fun `justify switches alignment`() {
        assertTrue(render(appearance.copy(justify = true)).contains("text-align:justify"))
    }

    @Test
    fun `notes and summary can be hidden`() {
        val hidden = render(appearance.copy(showNotes = false, showSummary = false))
        assertFalse(hidden.contains("Start note"))
        assertFalse(hidden.contains("End note"))
        assertFalse(hidden.contains("Summary text"))
        assertTrue(hidden.contains("""<p data-p="0">Body <em>text</em></p>"""))
    }

    @Test
    fun `an imported font is embedded and preferred`() {
        val css = "@font-face{font-family:'GoonReaderCustom';src:url(data:font/ttf;base64,AAAA)}"
        val html = render(appearance.copy(fontFaceCss = css))
        assertTrue(html.contains(css))
        assertTrue(html.contains("font-family:'GoonReaderCustom',system-ui"))
        assertFalse(render().contains("GoonReaderCustom"))
    }

    @Test
    fun `each theme paints its own colours`() {
        assertTrue(render(appearance.copy(palette = ReaderTheme.Night.palette())).contains("#17120E"))
        assertTrue(render(appearance.copy(palette = ReaderTheme.Day.palette())).contains("#FAF6F0"))
        assertTrue(render(appearance.copy(palette = ReaderTheme.Sepia.palette())).contains("#F1E6CF"))
        assertTrue(render(appearance.copy(palette = ReaderTheme.Amoled.palette())).contains("background:#000000"))
    }
}
