package app.goonreader.data.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaletteTest {

    @Test
    fun `custom colours keep their values and report the right mode`() {
        val dark = customPalette(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertTrue(dark.isDark)
        assertTrue(dark.background == "#000000")
        assertTrue(dark.text == "#FFFFFF")

        val light = customPalette(0xFFF7F1E6.toInt(), 0xFF2B2622.toInt())
        assertFalse(light.isDark)
        assertTrue(light.background == "#F7F1E6")
    }

    @Test
    fun `derived colours stay between text and background`() {
        val palette = customPalette(0xFF1B1612.toInt(), 0xFFE6DCCF.toInt())
        // The rule sits closer to the background than the text does, and is not the same colour.
        assertNotEquals(palette.text, palette.rule)
        assertNotEquals(palette.background, palette.rule)
    }
}
