package app.goonreader.data.parser

import app.goonreader.Fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterParserTest {

    private val chapterOne by lazy {
        checkNotNull(ChapterParser.parse(Fixture.load("05-work-chapter1.html"), "4701869", "10736366"))
    }

    @Test
    fun `reads chapter title and body`() {
        assertEquals("Chapter 1", chapterOne.title)
        assertTrue(chapterOne.bodyHtml.contains("dreaming of it since the defeat of Voldemort"))
    }

    @Test
    fun `strips screen-reader landmarks from the body`() {
        assertFalse(chapterOne.bodyHtml.contains("Chapter Text"))
        assertFalse(chapterOne.bodyHtml.contains("Work Text"))
    }

    @Test
    fun `keeps paragraph markup`() {
        assertTrue(chapterOne.bodyHtml.contains("<p>"))
    }

    @Test
    fun `second chapter parses independently`() {
        val chapterTwo = ChapterParser.parse(Fixture.load("07-chapter-later.html"), "4701869", "10736378")
        checkNotNull(chapterTwo)
        assertEquals("Chapter 2", chapterTwo.title)
        assertTrue(chapterTwo.bodyHtml.isNotEmpty())
        assertNull(chapterTwo.endNotesHtml)
    }

    @Test
    fun `single-chapter work has a body but no chapter title`() {
        val oneShot = ChapterParser.parse(Fixture.load("12-work-single-chapter.html"), "1372345", "1")
        checkNotNull(oneShot)
        assertTrue(oneShot.bodyHtml.contains("Blood is dripping"))
        assertNull(oneShot.title)
    }

    @Test
    fun `adult interstitial yields no chapter`() {
        assertNull(ChapterParser.parse(Fixture.load("09-adult-interstitial.html"), "2734082", "1"))
    }

    @Test
    fun `error page yields no chapter`() {
        assertNull(ChapterParser.parse(Fixture.load("11-work-not-found.html"), "1", "1"))
    }

    @Test
    fun `sanitiser removes scripts and event handlers`() {
        val dirty = """
            <p onclick="steal()">safe text</p>
            <script>alert(1)</script>
            <a href="javascript:evil()">link</a>
            <iframe src="https://elsewhere"></iframe>
        """.trimIndent()

        val clean = Ao3Html.clean(dirty)

        assertTrue(clean.contains("safe text"))
        assertFalse(clean.contains("<script"))
        assertFalse(clean.contains("onclick"))
        assertFalse(clean.contains("javascript:"))
        assertFalse(clean.contains("<iframe"))
    }
}
