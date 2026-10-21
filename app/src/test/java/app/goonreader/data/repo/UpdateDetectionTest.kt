package app.goonreader.data.repo

import app.goonreader.data.db.WorkEntity
import app.goonreader.domain.model.Work
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The decision that makes an update check fetch the chapter list at all (PRD 7.4). */
class UpdateDetectionTest {

    private fun stored(published: Int, updatedAt: Long? = null) = WorkEntity(
        id = "4701869",
        title = "A work",
        chaptersPublished = published,
        updatedAt = updatedAt,
    )

    private fun fresh(published: Int?, updatedAt: LocalDate? = null) = Work(
        id = "4701869",
        title = "A work",
        chaptersPublished = published,
        updatedAt = updatedAt,
    )

    @Test
    fun `one more published chapter is new data`() {
        assertTrue(hasNewChapterData(stored(published = 11), fresh(published = 12)))
    }

    @Test
    fun `the same count and date is not new data`() {
        val day = LocalDate.of(2024, 3, 5)
        assertFalse(hasNewChapterData(stored(11, day.toEpochMillis()), fresh(11, day)))
    }

    @Test
    fun `an edited chapter moves the updated date without the count`() {
        val old = LocalDate.of(2024, 3, 5)
        val edited = LocalDate.of(2024, 3, 9)
        assertTrue(hasNewChapterData(stored(11, old.toEpochMillis()), fresh(11, edited)))
    }

    @Test
    fun `a date that looks older is not new data`() {
        val old = LocalDate.of(2024, 3, 5)
        val older = LocalDate.of(2024, 3, 1)
        assertFalse(hasNewChapterData(stored(11, old.toEpochMillis()), fresh(11, older)))
    }

    @Test
    fun `a page without a chapter count falls back to what is stored`() {
        assertFalse(hasNewChapterData(stored(published = 11), fresh(published = null)))
    }

    @Test
    fun `a page without a date still detects a new chapter`() {
        assertTrue(hasNewChapterData(stored(published = 11), fresh(published = 12, updatedAt = null)))
    }
}
