package app.goonreader.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import app.goonreader.data.prefs.ReaderAction

/**
 * Maps a tap to one of the nine zones (PRD 10.2). Kept out of the composable so the grid maths
 * is testable without a device.
 */
internal fun zoneAction(position: Offset, size: IntSize, zones: List<ReaderAction>): ReaderAction {
    if (zones.size != 9 || size.width == 0 || size.height == 0) return ReaderAction.None
    val column = (position.x / size.width * 3f).toInt().coerceIn(0, 2)
    val row = (position.y / size.height * 3f).toInt().coerceIn(0, 2)
    return zones[row * 3 + column]
}
