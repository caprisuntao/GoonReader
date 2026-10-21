package app.goonreader.data.parser

import app.goonreader.Fixture
import app.goonreader.domain.model.TagRef
import app.goonreader.domain.model.TagType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagSearchParserTest {

    private val page by lazy { TagSearchParser.parse(Fixture.load("14-tag-search.html")) }

    @Test
    fun `parses the whole result list`() {
        assertEquals(50, page.size)
    }

    @Test
    fun `reads name, type, href and count`() {
        val first = page.first()
        assertEquals("!!TIME TRAVEL ALERT!!", first.name)
        assertEquals(TagType.Unsorted, first.type)
        // The href is copied verbatim: AO3 escapes characters in tag names itself.
        assertEquals("/tags/!!TIME%20TRAVEL%20ALERT!!", first.href)
        assertEquals(1, first.workCount)
    }

    @Test
    fun `keeps quoted names and their escapes`() {
        val quoted = page.first { it.name == "\"time travel\"" }
        assertEquals(TagType.Freeform, quoted.type)
        assertEquals(8, quoted.workCount)
        assertTrue(quoted.href.startsWith("/tags/%22time%20travel%22"))
    }

    @Test
    fun `every row has a tag link`() {
        assertTrue(page.all { it.href.startsWith("/tags/") })
        assertTrue(page.all { it.name.isNotBlank() })
    }

    @Test
    fun `parses the other tag types and missing counts`() {
        val html = """
            <ol class="tag index group">
              <li><span>Fandom: <a class="tag" href="/tags/Harry%20Potter">Harry Potter</a> (100)</span></li>
              <li><span>Character: <a class="tag" href="/tags/Hermione">Hermione</a> (20)</span></li>
              <li><span>Relationship: <a class="tag" href="/tags/A*s*B">A/B</a> (3)</span></li>
              <li><span>Freeform: <a class="tag" href="/tags/Time%20Travel">Time Travel</a></span></li>
            </ol>
        """.trimIndent()

        val tags: List<TagRef> = TagSearchParser.parse(html)

        assertEquals(4, tags.size)
        assertEquals(TagType.Fandom, tags[0].type)
        assertEquals(100, tags[0].workCount)
        assertEquals(TagType.Character, tags[1].type)
        assertEquals(TagType.Relationship, tags[2].type)
        assertEquals(TagType.Freeform, tags[3].type)
        assertNull(tags[3].workCount)
    }

    @Test
    fun `a page with no tag list parses to nothing`() {
        assertTrue(TagSearchParser.parse(Fixture.load("03-search-results-empty.html")).isEmpty())
    }
}
