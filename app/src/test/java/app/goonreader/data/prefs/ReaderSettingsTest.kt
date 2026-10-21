package app.goonreader.data.prefs

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsTest {

    @Test
    fun `tap zones round trip`() {
        val zones = ReaderSettings.DEFAULT_TAP_ZONES
        assertEquals(zones, ReaderSettings.tapZonesFrom(ReaderSettings.tapZonesToString(zones)))
    }

    @Test
    fun `a broken stored grid falls back to the default`() {
        val defaults = ReaderSettings.DEFAULT_TAP_ZONES
        assertEquals(defaults, ReaderSettings.tapZonesFrom(null))
        assertEquals(defaults, ReaderSettings.tapZonesFrom(""))
        assertEquals(defaults, ReaderSettings.tapZonesFrom("Menu,NextPage"))
        assertEquals(defaults, ReaderSettings.tapZonesFrom("Menu,Menu,Menu,Menu,Sideways,Menu,Menu,Menu,Menu"))
    }

    @Test
    fun `settings survive the preferences round trip`() {
        val custom = ReaderSettings(
            theme = ReaderTheme.Sepia,
            fontSizeSp = 22f,
            lineSpacing = 1.8f,
            paragraphSpacingEm = 0.5f,
            sideMarginDp = 30,
            verticalMarginDp = 20,
            justify = true,
            showSummary = false,
            showNotes = false,
            keepScreenOn = false,
            markReadThreshold = 0.9f,
            customBackground = 0xFF223344.toInt(),
            customText = 0xFFFFFFEE.toInt(),
            customFontPath = "/data/font.ttf",
            tapZones = List(9) { ReaderAction.Menu },
        )
        val prefs = mutablePreferencesOf()
        prefs.writeReaderSettings(custom)
        assertEquals(custom, prefs.toReaderSettings())
    }

    @Test
    fun `values outside the supported range are clamped on read`() {
        val prefs = mutablePreferencesOf()
        prefs.writeReaderSettings(ReaderSettings(fontSizeSp = 99f, markReadThreshold = 0.1f, sideMarginDp = 500))
        val read = prefs.toReaderSettings()
        assertEquals(36f, read.fontSizeSp)
        assertEquals(0.5f, read.markReadThreshold)
        assertEquals(64, read.sideMarginDp)
    }

    @Test
    fun `an empty store yields defaults`() {
        assertEquals(ReaderSettings(), mutablePreferencesOf().toReaderSettings())
    }
}
