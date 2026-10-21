package app.goonreader.data.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryCookieJarTest {

    private val url = "https://archiveofourown.org/works/1".toHttpUrl()
    private val jar = MemoryCookieJar()

    @Test
    fun `a saved cookie comes back on the next request`() {
        jar.saveFromResponse(url, listOf(cookie("cf_clearance", "first")))
        assertEquals(listOf("first"), jar.loadForRequest(url).map { it.value })
    }

    @Test
    fun `a rotated cookie replaces the old value`() {
        jar.saveFromResponse(url, listOf(cookie("cf_clearance", "first")))
        jar.saveFromResponse(url, listOf(cookie("cf_clearance", "second")))
        assertEquals(listOf("second"), jar.loadForRequest(url).map { it.value })
    }

    @Test
    fun `cookies stay on their own host`() {
        jar.saveFromResponse(url, listOf(cookie("cf_clearance", "ao3")))
        assertTrue(jar.loadForRequest("https://example.org/".toHttpUrl()).isEmpty())
    }

    private fun cookie(name: String, value: String): Cookie =
        Cookie.Builder()
            .name(name)
            .value(value)
            .hostOnlyDomain(url.host)
            .path("/")
            .build()
}
