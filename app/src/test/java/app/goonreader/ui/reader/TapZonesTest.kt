package app.goonreader.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import app.goonreader.data.prefs.ReaderAction
import org.junit.Assert.assertEquals
import org.junit.Test

class TapZonesTest {

    private val size = IntSize(300, 300)
    private val zones = List(9) { index -> ReaderAction.entries[index % ReaderAction.entries.size] }

    @Test
    fun `top left is the first zone and bottom right the last`() {
        assertEquals(zones[0], zoneAction(Offset(1f, 1f), size, zones))
        assertEquals(zones[8], zoneAction(Offset(299f, 299f), size, zones))
    }

    @Test
    fun `the centre tap opens the middle zone`() {
        assertEquals(zones[4], zoneAction(Offset(150f, 150f), size, zones))
    }

    @Test
    fun `row and column follow the tap position`() {
        assertEquals(zones[1], zoneAction(Offset(170f, 10f), size, zones))
        assertEquals(zones[3], zoneAction(Offset(10f, 170f), size, zones))
        assertEquals(zones[7], zoneAction(Offset(170f, 250f), size, zones))
    }

    @Test
    fun `a tap beyond the edge is clamped to the nearest cell`() {
        assertEquals(zones[2], zoneAction(Offset(900f, 10f), size, zones))
        assertEquals(zones[6], zoneAction(Offset(10f, 900f), size, zones))
    }

    @Test
    fun `an unusable grid is inert`() {
        assertEquals(ReaderAction.None, zoneAction(Offset(10f, 10f), size, emptyList()))
        assertEquals(ReaderAction.None, zoneAction(Offset(10f, 10f), IntSize.Zero, zones))
    }
}
