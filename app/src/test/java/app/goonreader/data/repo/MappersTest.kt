package app.goonreader.data.repo

import app.goonreader.data.db.ChapterEntity
import app.goonreader.data.db.WorkEntity
import app.goonreader.domain.model.ChapterRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    private fun chapter(
        id: String,
        number: Int,
        isRead: Boolean = false,
        progress: Float = 0f,
        bookmarked: Boolean = false,
    ) = ChapterEntity(
        id = id,
        workId = "4701869",
        number = number,
        title = "Chapter $number",
        publishedAt = null,
        fetchedAt = null,
        isRead = isRead,
        progress = progress,
        bookmarked = bookmarked,
        isDownloaded = false,
    )

    @Test
    fun `refresh keeps read state, progress and bookmarks`() {
        val existing = listOf(chapter("10736366", 1, isRead = true, progress = 0.62f, bookmarked = true))
        val incoming = listOf(ChapterRef("10736366", "4701869", 1, "Chapter 1", null))

        val merged = mergeChapters(existing, incoming, markNewAsFetched = true, now = 999L)

        assertEquals(1, merged.size)
        assertTrue(merged[0].isRead)
        assertEquals(0.62f, merged[0].progress)
        assertTrue(merged[0].bookmarked)
        // An existing chapter is not "new", so it must not appear in the Updates feed.
        assertNull(merged[0].fetchedAt)
    }

    @Test
    fun `a newly published chapter is stamped only on refresh`() {
        val incoming = listOf(ChapterRef("10736378", "4701869", 2, "Chapter 2", null))

        val refreshed = mergeChapters(emptyList(), incoming, markNewAsFetched = true, now = 123L)
        assertEquals(123L, refreshed[0].fetchedAt)

        val initialAdd = mergeChapters(emptyList(), incoming, markNewAsFetched = false, now = 123L)
        assertNull(initialAdd[0].fetchedAt)
    }

    @Test
    fun `chapter numbers come from the incoming list, not insertion order`() {
        val merged = mergeChapters(
            existing = emptyList(),
            incoming = listOf(
                ChapterRef("c3", "w", 3, "Third", null),
                ChapterRef("c1", "w", 1, "First", null),
            ),
            markNewAsFetched = false,
            now = 0L,
        )
        assertEquals(listOf(3, 1), merged.map { it.number })
    }

    @Test
    fun `work survives a round trip through domain`() {
        val entity = WorkEntity(
            id = "4701869",
            title = "Oh God Not Again!",
            authors = "Sarah1281",
            fandoms = "Harry Potter - J. K. Rowling",
            rating = "General Audiences",
            warnings = "No Archive Warnings Apply",
            categories = "Gen",
            relationships = "Draco Malfoy & Harry Potter",
            characters = "Harry Potter",
            tags = "Time Travel\nFix-It",
            summary = "<p>Summary</p>",
            language = "English",
            wordCount = 151_873L,
            chaptersPublished = 50,
            chaptersTotal = 50,
            isComplete = true,
            publishedAt = 1_440_000_000_000L,
            updatedAt = 1_440_000_000_000L,
            inLibrary = true,
            addedAt = 777L,
        )

        val domain = entity.toDomain()
        assertEquals(listOf("Time Travel", "Fix-It"), domain.tags)
        assertEquals(listOf("Harry Potter - J. K. Rowling"), domain.fandoms)
        assertTrue(domain.isComplete)

        val back = domain.toEntity(existing = entity, inLibrary = true, addedAt = 1L)
        assertEquals("Time Travel\nFix-It", back.tags)
        assertEquals(151_873L, back.wordCount)
        // addedAt belongs to the user's library, not to the refresh.
        assertEquals(777L, back.addedAt)
    }

    @Test
    fun `blank columns read as empty lists and nulls`() {
        val domain = WorkEntity(id = "1", title = "Untitled").toDomain()
        assertTrue(domain.authors.isEmpty())
        assertTrue(domain.tags.isEmpty())
        assertNull(domain.rating)
        assertNull(domain.summaryHtml)
        assertFalse(domain.isComplete)
    }
}
