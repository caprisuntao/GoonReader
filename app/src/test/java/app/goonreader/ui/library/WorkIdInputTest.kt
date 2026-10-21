package app.goonreader.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkIdInputTest {

    @Test
    fun `accepts a work link`() {
        assertEquals("4701869", parseWorkId("https://archiveofourown.org/works/4701869"))
    }

    @Test
    fun `accepts a link that points at a chapter`() {
        assertEquals("4701869", parseWorkId("https://archiveofourown.org/works/4701869/chapters/10736366"))
    }

    @Test
    fun `accepts a link with a query string`() {
        assertEquals("4701869", parseWorkId("https://archiveofourown.org/works/4701869?view_adult=true"))
    }

    @Test
    fun `accepts a bare id with whitespace`() {
        assertEquals("4701869", parseWorkId("  4701869 "))
    }

    @Test
    fun `rejects things that are not work ids`() {
        assertNull(parseWorkId(""))
        assertNull(parseWorkId("hello"))
        assertNull(parseWorkId("https://archiveofourown.org/tags/Harry%20Potter/works"))
    }
}
