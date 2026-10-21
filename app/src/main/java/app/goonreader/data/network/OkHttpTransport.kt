package app.goonreader.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Cookie
import okhttp3.CookieJar

/**
 * Cookie storage for the session. AO3 sets `cf_clearance` and adult-content consent cookies;
 * keeping them means a challenge cleared once stays cleared until the app is killed.
 *
 * `shortcut: in-memory only, cookies are lost on process death; persist with DataStore in M6.`
 */
class MemoryCookieJar : CookieJar {

    /** `name;path` → cookie, so a re-set cookie replaces the old value instead of stacking up. */
    private val store = mutableMapOf<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(store) {
            val forHost = store.getOrPut(url.host) { mutableMapOf() }
            cookies.forEach { forHost["${it.name};${it.path}"] = it }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(store) {
        store[url.host].orEmpty().values.filter { it.matches(url) }
    }
}

/**
 * OkHttp transport. Headers are deliberately browser-like because AO3 sits behind Cloudflare,
 * while the User-Agent still identifies the app (PRD 7.3).
 */
class OkHttpTransport(
    private val client: OkHttpClient,
    private val userAgent: String,
) : Ao3Transport {

    override suspend fun get(url: String): Ao3Raw = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Upgrade-Insecure-Requests", "1")
            // Client hints + fetch metadata, as a mobile Chrome sends them. Cloudflare's bot
            // filter scores their absence; they cost nothing to include.
            .header("sec-ch-ua", BROWSER_HINTS)
            .header("sec-ch-ua-mobile", "?1")
            .header("sec-ch-ua-platform", "\"Android\"")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-User", "?1")
            .header("Sec-Fetch-Dest", "document")
            .header("Referer", "https://archiveofourown.org/")
            .build()

        client.newCall(request).execute().use { response ->
            Ao3Raw(
                code = response.code,
                body = response.body?.string().orEmpty(),
                retryAfterSeconds = response.header("Retry-After")?.toLongOrNull(),
            )
        }
    }

    private companion object {
        /** Must describe the same browser version as the User-Agent. */
        const val BROWSER_HINTS = "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\""
    }
}
