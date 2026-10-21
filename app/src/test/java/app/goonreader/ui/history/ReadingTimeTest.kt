package app.goonreader.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTimeTest {

    @Test
    fun `short sessions read as under a minute`() {
        assertEquals("under a minute", readingTime(0))
        assertEquals("under a minute", readingTime(59_000))
    }

    @Test
    fun `minutes and hours are rounded down`() {
        assertEquals("12 min", readingTime(12 * 60_000L))
        assertEquals("1 h 5 min", readingTime(65 * 60_000L))
    }
}
