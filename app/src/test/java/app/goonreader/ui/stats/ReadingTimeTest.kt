package app.goonreader.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTimeTest {

    @Test
    fun `minutes, hours and the boundary between them`() {
        assertEquals("0 min", formatReadingTime(0))
        assertEquals("0 min", formatReadingTime(59_999))
        assertEquals("1 min", formatReadingTime(60_000))
        assertEquals("47 min", formatReadingTime(47 * 60_000L))
        assertEquals("1 h", formatReadingTime(3_600_000))
        assertEquals("3 h 12 min", formatReadingTime((3 * 60 + 12) * 60_000L))
    }

    @Test
    fun `negative durations never render as negative time`() {
        assertEquals("0 min", formatReadingTime(-5_000))
    }
}
