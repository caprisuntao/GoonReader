package app.goonreader.data.network

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Last-resort transport for Cloudflare's managed challenge.
 *
 * AO3 sits behind Cloudflare, which fingerprints TLS; OkHttp cannot be made to look like
 * Chrome no matter which headers it sends (verified on device). So when — and only when — a
 * request comes back challenged, [Ao3Client] replays that one request here. A real WebView
 * engine solves the challenge on the same IP, and its User-Agent is set to the same string the
 * rest of the app sends, so a `cf_clearance` cookie it earns stays valid for those requests.
 *
 * Runs on the main thread because WebView requires it; the view is never attached to a window.
 *
 * On success the WebView's cookies are handed to [cookieJar]. That is the whole point of the
 * fallback being cheap: the view has just solved the challenge and holds `cf_clearance`, so every
 * later request on the plain HTTP path is answered instead of challenged.
 */
class WebViewTransport(
    private val context: Context,
    private val userAgent: String,
    private val cookieJar: CookieJar,
) : Ao3Transport {

    override suspend fun get(url: String): Ao3Raw = withContext(Dispatchers.Main) {
        val webView = createWebView()
        try {
            webView.loadUrl(url)
            awaitPage(webView, url)
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = userAgent
        webViewClient = WebViewClient()
    }

    /** Cloudflare's challenge resolves itself once its JS runs; poll until the title settles. */
    private suspend fun awaitPage(webView: WebView, url: String): Ao3Raw {
        var ready = ""
        var title = ""
        repeat(MAX_POLLS) {
            delay(POLL_INTERVAL_MILLIS)
            ready = evaluate(webView, "document.readyState").jsonString()
            title = evaluate(webView, "document.title").jsonString()
            if (ready == "complete" && title.isNotEmpty() && !title.contains("just a moment", ignoreCase = true)) {
                val html = evaluate(webView, "document.documentElement.outerHTML").jsonString()
                if (html.isNotEmpty()) {
                    val cookies = syncCookies(url)
                    Log.d(TAG, "fallback ok: ${html.length} chars, $cookies cookies, title='$title'")
                    return Ao3Raw(code = 200, body = html)
                }
            }
        }
        // Still challenged or never loaded; the caller keeps its challenge verdict.
        Log.d(TAG, "fallback gave up on $url: ready='$ready' title='$title'")
        return Ao3Raw(code = 503, body = "")
    }

    /** Hands the challenge cookie to OkHttp; returns how many cookies were copied. */
    private fun syncCookies(url: String): Int {
        val header = CookieManager.getInstance().getCookie(url) ?: return 0
        val httpUrl = url.toHttpUrlOrNull() ?: return 0
        val cookies = parseCookieHeader(header, httpUrl)
        cookieJar.saveFromResponse(httpUrl, cookies)
        return cookies.size
    }

    private suspend fun evaluate(webView: WebView, script: String): String =
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { value ->
                if (continuation.isActive) continuation.resume(value)
            }
        }

    /** `evaluateJavascript` hands back a JSON literal; unwrap it to the raw string. */
    private fun String?.jsonString(): String {
        val raw = this ?: return ""
        if (raw == "null") return ""
        return runCatching { JSONTokener(raw).nextValue() as String }.getOrElse { raw }
    }

    private companion object {
        const val TAG = "GoonReader"
        const val POLL_INTERVAL_MILLIS = 700L
        const val MAX_POLLS = 26
    }
}
