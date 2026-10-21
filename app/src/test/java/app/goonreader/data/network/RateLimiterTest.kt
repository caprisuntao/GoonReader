package app.goonreader.data.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    /**
     * Real time on purpose: the virtual clock in `runTest` would skip the delays this class
     * exists to enforce, so the test could never fail.
     */
    @Test
    fun `enforces a minimum gap between turns`() = runBlocking {
        val limiter = RateLimiter(interactiveMillis = 80)
        val started = System.nanoTime()

        repeat(3) { limiter.awaitTurn() }

        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue("expected two 80ms gaps, took ${elapsedMillis}ms", elapsedMillis >= 150)
    }

    /** Background batches use the slower gap; one limiter, two speeds (PRD 7.3). */
    @Test
    fun `background turns use the background gap`() = runBlocking {
        val limiter = RateLimiter(interactiveMillis = 10, backgroundMillis = 150)
        val started = System.nanoTime()

        repeat(2) { limiter.awaitTurn(background = true) }

        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue("expected a 150ms background gap, took ${elapsedMillis}ms", elapsedMillis >= 130)
    }
}
