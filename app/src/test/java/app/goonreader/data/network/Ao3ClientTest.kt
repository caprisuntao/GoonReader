package app.goonreader.data.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class Ao3ClientTest {

    /** Zero delay: these tests are about retry/challenge logic, not pacing. */
    private val instant = RateLimiter(interactiveMillis = 0, backgroundMillis = 0)

    @Test
    fun `returns html on success`() = runTest {
        val client = Ao3Client(instant, Ao3Transport { Ao3Raw(200, "<html>ok</html>") })
        assertEquals(Ao3Page.Success("<html>ok</html>"), client.get("https://archiveofourown.org/works/1"))
    }

    @Test
    fun `cloudflare challenge is reported as its own outcome`() = runTest {
        val body = "<script src=\"/cdn-cgi/challenge-platform/h/b/orchestrate/jsch/v1\"></script>"
        val client = Ao3Client(instant, Ao3Transport { Ao3Raw(403, body) })
        assertEquals(Ao3Page.ChallengeRequired, client.get("https://archiveofourown.org/works/1"))
    }

    @Test
    fun `429 is retried and then succeeds`() = runTest {
        var calls = 0
        val client = Ao3Client(
            instant,
            Ao3Transport {
                calls++
                if (calls == 1) Ao3Raw(429, "", retryAfterSeconds = 1) else Ao3Raw(200, "ok")
            },
        )
        assertEquals(Ao3Page.Success("ok"), client.get("https://archiveofourown.org/works/1"))
        assertEquals(2, calls)
    }

    @Test
    fun `retries stop after three attempts`() = runTest {
        var calls = 0
        val client = Ao3Client(instant, Ao3Transport { calls++; Ao3Raw(503, "server down") })
        assertEquals(Ao3Page.HttpError(503), client.get("https://archiveofourown.org/works/1"))
        assertEquals(3, calls)
    }

    @Test
    fun `404 is not retried`() = runTest {
        var calls = 0
        val client = Ao3Client(instant, Ao3Transport { calls++; Ao3Raw(404, "") })
        assertEquals(Ao3Page.NotFound, client.get("https://archiveofourown.org/works/1"))
        assertEquals(1, calls)
    }

    @Test
    fun `challenge is resolved through the browser fallback`() = runTest {
        val client = Ao3Client(
            instant,
            Ao3Transport { Ao3Raw(403, "challenge-platform") },
            Ao3Transport { Ao3Raw(200, "<html>real</html>") },
        )
        assertEquals(Ao3Page.Success("<html>real</html>"), client.get("https://archiveofourown.org/works/1"))
    }

    @Test
    fun `fallback that stays challenged still reports a challenge`() = runTest {
        val client = Ao3Client(
            instant,
            Ao3Transport { Ao3Raw(403, "challenge-platform") },
            Ao3Transport { Ao3Raw(403, "") },
        )
        assertEquals(Ao3Page.ChallengeRequired, client.get("https://archiveofourown.org/works/1"))
    }

    @Test
    fun `fallback is not consulted for ordinary successes`() = runTest {
        var fallbackCalls = 0
        val client = Ao3Client(
            instant,
            Ao3Transport { Ao3Raw(200, "ok") },
            Ao3Transport { fallbackCalls++; Ao3Raw(200, "unused") },
        )
        assertEquals(Ao3Page.Success("ok"), client.get("https://archiveofourown.org/works/1"))
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `io failure becomes a network error`() = runTest {
        val client = Ao3Client(instant, Ao3Transport { throw IOException("offline") })
        assertTrue(client.get("https://archiveofourown.org/works/1") is Ao3Page.NetworkError)
    }
}
