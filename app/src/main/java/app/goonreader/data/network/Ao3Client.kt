package app.goonreader.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** What one HTTP GET returned, before interpretation. */
data class Ao3Raw(
    val code: Int,
    val body: String,
    /** Parsed `Retry-After` header, in seconds. */
    val retryAfterSeconds: Long? = null,
)

/**
 * The single seam between [Ao3Client] and OkHttp. Tests supply a fake to exercise retry and
 * challenge handling without a web server or network.
 */
fun interface Ao3Transport {
    suspend fun get(url: String): Ao3Raw
}

/** Interpreted result of a fetch. Callers must handle every branch. */
sealed interface Ao3Page {
    data class Success(val html: String) : Ao3Page
    data object NotFound : Ao3Page

    /** Cloudflare interstitial: needs a browser, not a retry. */
    data object ChallengeRequired : Ao3Page
    data class HttpError(val code: Int) : Ao3Page
    data class NetworkError(val cause: IOException) : Ao3Page
}

/**
 * Fetches AO3 pages politely: every attempt goes through the shared [RateLimiter], only one
 * request is in flight at a time, transient server errors are retried with exponential backoff
 * (honouring `Retry-After`), and a Cloudflare challenge is reported as its own state instead of
 * masquerading as a parse failure.
 */
class Ao3Client(
    private val rateLimiter: RateLimiter,
    private val transport: Ao3Transport,
    /**
     * Optional browser-engine fallback, used only when [transport] is challenged. Every request
     * still starts on the cheap path; this is the escape hatch, not the default.
     */
    private val browserFallback: Ao3Transport? = null,
) {

    /**
     * The limiter spaces out request *starts*; this keeps the previous one from still being in
     * flight. PRD 7.3 asks for serialised requests, and a WebView challenge replay takes seconds.
     */
    private val inFlight = Mutex()

    suspend fun get(url: String, background: Boolean = false): Ao3Page {
        var attempt = 1
        while (true) {
            rateLimiter.awaitTurn(background)
            val raw = try {
                send(transport, url)
            } catch (e: IOException) {
                return Ao3Page.NetworkError(e)
            }

            if (isChallenge(raw)) {
                val viaBrowser = browserFallback?.let { fallback ->
                    runCatching { send(fallback, url) }.getOrNull()
                }
                return if (viaBrowser != null && viaBrowser.code in 200..299 && !isChallenge(viaBrowser)) {
                    Ao3Page.Success(viaBrowser.body)
                } else {
                    Ao3Page.ChallengeRequired
                }
            }

            when {
                raw.code == 404 -> return Ao3Page.NotFound
                raw.code in 200..299 -> return Ao3Page.Success(raw.body)
                raw.code in RETRYABLE -> {
                    if (attempt >= MAX_ATTEMPTS) return Ao3Page.HttpError(raw.code)
                    delay(backoffMillis(raw, attempt))
                    attempt++
                }
                else -> return Ao3Page.HttpError(raw.code)
            }
        }
    }

    private suspend fun send(via: Ao3Transport, url: String): Ao3Raw =
        inFlight.withLock { via.get(url) }

    private fun backoffMillis(raw: Ao3Raw, attempt: Int): Long =
        raw.retryAfterSeconds?.let { it * 1_000L } ?: minOf(MAX_BACKOFF_MILLIS, 1_000L shl attempt)

    /**
     * A challenge is a 403, or Cloudflare's interstitial whatever status it uses. The presence
     * of the `challenge-platform` script alone is not a signal: Cloudflare injects its beacon
     * into ordinary pages too, so testing for it would send every good page through the
     * (slower) browser fallback.
     */
    private fun isChallenge(raw: Ao3Raw): Boolean =
        raw.code == 403 ||
            raw.body.contains("Just a moment") ||
            raw.body.contains("Enable JavaScript and cookies to continue")

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val MAX_BACKOFF_MILLIS = 30_000L
        val RETRYABLE = setOf(429, 500, 502, 503, 504)
    }
}
