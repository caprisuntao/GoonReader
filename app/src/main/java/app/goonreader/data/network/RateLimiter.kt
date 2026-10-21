package app.goonreader.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises every AO3 request and keeps a minimum gap between them.
 *
 * One instance is shared app-wide (see `NetworkModule`); per-feature limiters would let
 * a background update and an open chapter stack two requests at once, which is exactly what
 * the PRD forbids. Interactive and background work need different gaps, so the caller says
 * which kind of request this is rather than each feature owning a limiter.
 *
 * The intervals are mutable because PRD 9.8 exposes the interactive one as a setting; the
 * settings applier writes it in place so no caller has to be reconstructed.
 */
class RateLimiter(
    interactiveMillis: Long = DEFAULT_DELAY_MILLIS,
    backgroundMillis: Long = BACKGROUND_DELAY_MILLIS,
) {

    @Volatile
    var interactiveMillis: Long = interactiveMillis

    @Volatile
    var backgroundMillis: Long = backgroundMillis

    private val mutex = Mutex()
    private var lastRequestNanos = 0L

    /** Suspends until this request may go out, then claims the slot. */
    suspend fun awaitTurn(background: Boolean = false) = mutex.withLock {
        val gap = if (background) backgroundMillis else interactiveMillis
        if (lastRequestNanos != 0L) {
            val elapsedMillis = (System.nanoTime() - lastRequestNanos) / NANOS_PER_MILLI
            val remaining = gap - elapsedMillis
            if (remaining > 0) delay(remaining)
        }
        lastRequestNanos = System.nanoTime()
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L

        /** User-initiated requests; PRD 7.3 default. */
        const val DEFAULT_DELAY_MILLIS = 2_000L

        /** Background batches; PRD 7.3 default. */
        const val BACKGROUND_DELAY_MILLIS = 5_000L
    }
}
