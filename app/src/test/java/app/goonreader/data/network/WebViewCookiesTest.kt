package app.goonreader.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewCookiesTest {

    private val url = "https://archiveofourown.org/works/4701869".toHttpUrl()

    @Test
    fun `every name-value pair becomes a cookie scoped to the host`() {
        val cookies = parseCookieHeader("cf_clearance=abc123; _cfuvid=xyz-1", url)
        assertEquals(listOf("cf_clearance", "_cfuvid"), cookies.map { it.name })
        assertEquals("abc123", cookies.first().value)
        assertTrue(cookies.all { it.domain == "archiveofourown.org" })
    }

    @Test
    fun `equals signs inside a value survive`() {
        assertEquals("a=b==", parseCookieHeader("cf_clearance=a=b==", url).single().value)
    }

    @Test
    fun `empty and nameless pairs are skipped`() {
        assertEquals(listOf("ok"), parseCookieHeader("; =broken; ok=1;", url).map { it.name })
    }

    @Test
    fun `a missing header yields nothing`() {
        assertTrue(parseCookieHeader(null, url).isEmpty())
        assertTrue(parseCookieHeader("  ", url).isEmpty())
    }
}
