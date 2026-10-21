package app.goonreader.data.backup

import app.goonreader.data.db.BookmarkEntity
import app.goonreader.data.db.CategoryEntity
import app.goonreader.data.db.ChapterEntity
import app.goonreader.data.db.WorkCategoryCrossRefEntity
import app.goonreader.data.db.WorkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun sample() = BackupData(
        works = listOf(
            WorkEntity(
                id = "1",
                title = "A & B \"quoted\"",
                authors = "Someone\nSomeone else",
                summary = "line one\nline two — em dash",
                wordCount = 12_345L,
                chaptersPublished = 3,
                chaptersTotal = null,
                inLibrary = true,
                addedAt = 1_700_000_000_000L,
                lastCheckedAt = null,
                kudos = 42,
            ),
        ),
        chapters = listOf(
            ChapterEntity(
                id = "11",
                workId = "1",
                number = 1,
                title = "Start",
                fetchedAt = 123L,
                isRead = true,
                progress = 0.75f,
                isDownloaded = true,
            ),
        ),
        categories = listOf(CategoryEntity(id = 3, name = "Cozy")),
        memberships = listOf(WorkCategoryCrossRefEntity(workId = "1", categoryId = 3)),
        bookmarks = listOf(
            BookmarkEntity(
                id = 7,
                workId = "1",
                chapterId = "11",
                chapterNumber = 1,
                chapterTitle = "Start",
                progress = 0.42f,
                createdAt = 456L,
            ),
        ),
    )

    @Test
    fun `a library survives the JSON round trip`() {
        val data = sample()
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }

    @Test
    fun `the file says what it is`() {
        val json = BackupCodec.encode(sample())
        assertTrue(json.contains("\"format\": \"${BackupCodec.FORMAT}\""))
        assertTrue(json.contains("\"version\": 1"))
    }

    @Test
    fun `a foreign or broken file is rejected, not half imported`() {
        assertNull(BackupCodec.decode("not json at all"))
        assertNull(BackupCodec.decode("""{"format":"something-else","version":1}"""))
        assertNull(BackupCodec.decode("""{"format":"${BackupCodec.FORMAT}","version":99}"""))
    }

    @Test
    fun `one broken row is skipped and the rest still decodes`() {
        val broken = """
            {"format":"${BackupCodec.FORMAT}","version":1,
             "works":[{"id":"1","title":"Good","inLibrary":true,"addedAt":5},
                      {"title":"no id"}],
             "chapters":[]}
        """.trimIndent()

        val data = BackupCodec.decode(broken)
        assertEquals(1, data?.works?.size)
        assertEquals("Good", data?.works?.single()?.title)
    }

    @Test
    fun `missing optional rows are an empty backup, not a crash`() {
        val minimal = """{"format":"${BackupCodec.FORMAT}","version":1}"""
        assertEquals(BackupData(), BackupCodec.decode(minimal))
    }
}
