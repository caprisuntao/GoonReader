package app.goonreader.ui.updates

import app.goonreader.data.db.ChapterEntity
import app.goonreader.data.db.UpdateRow
import app.goonreader.ui.components.dayLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class UpdatesGroupingTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun row(id: String, fetchedAt: Long, isRead: Boolean = false) = UpdateRow(
        chapter = ChapterEntity(
            id = id,
            workId = "4701869",
            number = id.toInt(),
            title = "Chapter $id",
            fetchedAt = fetchedAt,
            isRead = isRead,
        ),
        workTitle = "A work",
    )

    private fun at(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    /**
     * The bug this pins: grouping by epoch-millis / 86_400_000 would call both of these
     * "1 January" because that is their UTC day. The feed must follow the device's day.
     */
    @Test
    fun `rows group by the local day, newest first`() {
        val lateNight = row("1", at(LocalDate.of(2024, 1, 1), LocalTime.of(23, 30)))
        val afterMidnight = row("2", at(LocalDate.of(2024, 1, 2), LocalTime.of(0, 30)))

        val days = listOf(lateNight, afterMidnight).toDays(zone)

        assertEquals(listOf(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 1)), days.map { it.date })
        assertEquals(listOf("2"), days[0].entries.map { it.chapterId })
        assertEquals(listOf("1"), days[1].entries.map { it.chapterId })
    }

    @Test
    fun `entries carry the fields the feed shows`() {
        val entry = listOf(row("7", at(LocalDate.of(2024, 1, 3), LocalTime.NOON), isRead = true))
            .toDays(zone)
            .single()
            .entries
            .single()

        assertEquals("4701869", entry.workId)
        assertEquals("7", entry.chapterId)
        assertEquals("A work", entry.workTitle)
        assertEquals(7, entry.number)
        assertEquals("Chapter 7", entry.title)
        assertEquals(true, entry.isRead)
    }

    @Test
    fun `day labels read like the rest of the app`() {
        val today = LocalDate.of(2024, 6, 10)

        assertEquals("Today", dayLabel(today, today))
        assertEquals("Yesterday", dayLabel(today.minusDays(1), today))
        assertEquals("8 Jun 2024", dayLabel(today.minusDays(2), today))
    }
}
