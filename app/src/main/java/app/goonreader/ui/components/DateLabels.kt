package app.goonreader.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/**
 * Epoch millis to the device-local day. This is deliberately not the date-only conversion used
 * for AO3 published dates: those are already days, while a timestamp must follow the user's zone.
 */
fun Long.toLocalDay(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DAY_FORMAT)
}
