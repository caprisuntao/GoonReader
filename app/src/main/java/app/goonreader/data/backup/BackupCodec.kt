package app.goonreader.data.backup

import app.goonreader.data.db.BookmarkEntity
import app.goonreader.data.db.CategoryEntity
import app.goonreader.data.db.ChapterEntity
import app.goonreader.data.db.WorkCategoryCrossRefEntity
import app.goonreader.data.db.WorkEntity
import org.json.JSONArray
import org.json.JSONObject

/** Everything a backup restores (PRD 9.8). History is not included: it is not library data. */
data class BackupData(
    val works: List<WorkEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val memberships: List<WorkCategoryCrossRefEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
)

/**
 * JSON export/import for the library.
 *
 * Only the library travels: works, their chapters (progress and read state), categories,
 * category membership and bookmarks. Download files stay on the device; an import re-marks a
 * chapter as downloaded only if its file is still there. Rows that do not parse are skipped
 * rather than failing the whole file, so one bad entry cannot cost the user the rest.
 */
object BackupCodec {

    const val FORMAT = "goonreader-backup"
    const val VERSION = 1

    fun encode(data: BackupData, exportedAt: Long = System.currentTimeMillis()): String =
        JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put("works", JSONArray(data.works.map { it.toJson() }))
            .put("chapters", JSONArray(data.chapters.map { it.toJson() }))
            .put("categories", JSONArray(data.categories.map { it.toJson() }))
            .put("memberships", JSONArray(data.memberships.map { it.toJson() }))
            .put("bookmarks", JSONArray(data.bookmarks.map { it.toJson() }))
            .toString(2)

    /** Null when the text is not a backup this version understands. */
    fun decode(text: String): BackupData? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (root.optString("format") != FORMAT || root.optInt("version") != VERSION) return null

        return BackupData(
            works = root.rows("works") { it.toWork() },
            chapters = root.rows("chapters") { it.toChapter() },
            categories = root.rows("categories") { CategoryEntity(it.getLong("id"), it.getString("name")) },
            memberships = root.rows("memberships") {
                WorkCategoryCrossRefEntity(it.getString("workId"), it.getLong("categoryId"))
            },
            bookmarks = root.rows("bookmarks") { it.toBookmark() },
        )
    }

    private fun <T> JSONObject.rows(name: String, read: (JSONObject) -> T): List<T> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONObject(index) ?: return@mapNotNull null
            runCatching { read(row) }.getOrNull()
        }
    }

    private fun WorkEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("authors", authors)
        put("fandoms", fandoms)
        put("rating", rating)
        put("warnings", warnings)
        put("categories", categories)
        put("relationships", relationships)
        put("characters", characters)
        put("tags", tags)
        put("summary", summary)
        put("language", language)
        put("wordCount", wordCount)
        put("chaptersPublished", chaptersPublished)
        put("chaptersTotal", chaptersTotal ?: JSONObject.NULL)
        put("isComplete", isComplete)
        put("publishedAt", publishedAt ?: JSONObject.NULL)
        put("updatedAt", updatedAt ?: JSONObject.NULL)
        put("inLibrary", inLibrary)
        put("addedAt", addedAt)
        put("lastCheckedAt", lastCheckedAt ?: JSONObject.NULL)
        put("kudos", kudos ?: JSONObject.NULL)
        put("hits", hits ?: JSONObject.NULL)
        put("bookmarks", bookmarks ?: JSONObject.NULL)
        put("commentCount", commentCount ?: JSONObject.NULL)
    }

    private fun JSONObject.toWork() = WorkEntity(
        id = getString("id"),
        title = getString("title"),
        authors = optString("authors"),
        fandoms = optString("fandoms"),
        rating = optString("rating"),
        warnings = optString("warnings"),
        categories = optString("categories"),
        relationships = optString("relationships"),
        characters = optString("characters"),
        tags = optString("tags"),
        summary = optString("summary"),
        language = optString("language"),
        wordCount = optLong("wordCount"),
        chaptersPublished = optInt("chaptersPublished"),
        chaptersTotal = optIntOrNull("chaptersTotal"),
        isComplete = optBoolean("isComplete"),
        publishedAt = optLongOrNull("publishedAt"),
        updatedAt = optLongOrNull("updatedAt"),
        inLibrary = optBoolean("inLibrary"),
        addedAt = optLong("addedAt"),
        lastCheckedAt = optLongOrNull("lastCheckedAt"),
        kudos = optIntOrNull("kudos"),
        hits = optLongOrNull("hits"),
        bookmarks = optIntOrNull("bookmarks"),
        commentCount = optIntOrNull("commentCount"),
    )

    private fun ChapterEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("workId", workId)
        put("number", number)
        put("title", title)
        put("publishedAt", publishedAt ?: JSONObject.NULL)
        put("fetchedAt", fetchedAt ?: JSONObject.NULL)
        put("isRead", isRead)
        put("progress", progress.toDouble())
        put("bookmarked", bookmarked)
        put("isDownloaded", isDownloaded)
    }

    private fun JSONObject.toChapter() = ChapterEntity(
        id = getString("id"),
        workId = getString("workId"),
        number = getInt("number"),
        title = optString("title"),
        publishedAt = optLongOrNull("publishedAt"),
        fetchedAt = optLongOrNull("fetchedAt"),
        isRead = optBoolean("isRead"),
        progress = optDouble("progress").toFloat(),
        bookmarked = optBoolean("bookmarked"),
        isDownloaded = optBoolean("isDownloaded"),
    )

    private fun CategoryEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
    }

    private fun WorkCategoryCrossRefEntity.toJson() = JSONObject().apply {
        put("workId", workId)
        put("categoryId", categoryId)
    }

    private fun BookmarkEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("workId", workId)
        put("chapterId", chapterId)
        put("chapterNumber", chapterNumber)
        put("chapterTitle", chapterTitle)
        put("progress", progress.toDouble())
        put("createdAt", createdAt)
    }

    private fun JSONObject.toBookmark() = BookmarkEntity(
        id = optLong("id"),
        workId = getString("workId"),
        chapterId = getString("chapterId"),
        chapterNumber = getInt("chapterNumber"),
        chapterTitle = optString("chapterTitle"),
        progress = optDouble("progress").toFloat(),
        createdAt = optLong("createdAt"),
    )

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (isNull(name)) null else optLong(name)

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (isNull(name)) null else optInt(name)
}
