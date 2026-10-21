package app.goonreader.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ao3UrlsCommentsTest {

    @Test
    fun `comments are the chapter page with show_comments and a page number`() {
        val url = Ao3Urls.comments("4701869", "10736366", page = 2).toHttpUrl()

        assertEquals("/works/4701869/chapters/10736366", url.encodedPath)
        assertEquals("true", url.queryParameter("show_comments"))
        assertEquals("2", url.queryParameter("page"))
        // The adult check is part of the chapter URL and must survive the rebuild.
        assertEquals("true", url.queryParameter("view_adult"))
    }

    @Test
    fun `a one-shot's comments come from the work page`() {
        val url = Ao3Urls.comments("4701869", "4701869", page = 1).toHttpUrl()

        assertEquals("/works/4701869", url.encodedPath)
        assertNull(url.queryParameter("page"))
        assertEquals("true", url.queryParameter("show_comments"))
    }
}
