package app.goonreader.data.prefs

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesTest {

    private fun prefs(vararg values: Preferences.Pair<*>): Preferences = mutablePreferencesOf(*values)

    @Test
    fun `defaults when nothing is stored`() {
        val settings = prefs().toAppSettings()

        assertEquals(ThemeMode.Dark, settings.themeMode)
        assertEquals(Accent.Orange, settings.accent)
        assertEquals(UpdateInterval.TwelveHours, settings.updateInterval)
        assertEquals(5, settings.downloadAhead)
        assertEquals(2_000L, settings.networkDelayMs)
        assertFalse(settings.incognito)
        assertFalse(settings.downloadedOnly)
    }

    @Test
    fun `a stored value wins`() {
        val settings = prefs(
            AppKeys.ACCENT to Accent.Blue.name,
            AppKeys.INCOGNITO to true,
            AppKeys.UPDATE_INTERVAL to UpdateInterval.Daily.name,
        ).toAppSettings()

        assertEquals(Accent.Blue, settings.accent)
        assertTrue(settings.incognito)
        assertEquals(UpdateInterval.Daily, settings.updateInterval)
    }

    @Test
    fun `an unknown stored name falls back to the default`() {
        val settings = prefs(AppKeys.THEME_MODE to "Neon").toAppSettings()

        assertEquals(ThemeMode.Dark, settings.themeMode)
    }

    @Test
    fun `out of range numbers are clamped`() {
        val settings = prefs(
            AppKeys.DOWNLOAD_AHEAD to 99,
            AppKeys.NETWORK_DELAY to 10L,
        ).toAppSettings()

        assertEquals(20, settings.downloadAhead)
        assertEquals(500L, settings.networkDelayMs)
    }
}
