package app.goonreader.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A work's metadata and its place in the library.
 *
 * Multi-valued AO3 fields (authors, fandoms, tags, ...) are stored newline-separated.
 * That is a deliberate M0 simplification: AO3 tags cannot contain newlines, and it keeps
 * list reads to a single row. If per-tag queries are ever needed (tag filtering inside the
 * library), these become their own tables with a migration.
 *
 * Timestamps are epoch milliseconds, null when AO3 does not publish the value.
 */
@Entity(
    tableName = "works",
    indices = [Index("inLibrary"), Index("updatedAt")],
)
data class WorkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: String = "",
    val fandoms: String = "",
    val rating: String = "",
    val warnings: String = "",
    val categories: String = "",
    val relationships: String = "",
    val characters: String = "",
    val tags: String = "",
    val summary: String = "",
    val language: String = "",
    val wordCount: Long = 0L,
    /** Chapters AO3 says are published, e.g. the 50 in "50/50". */
    val chaptersPublished: Int = 0,
    /** Projected total, null when AO3 shows "?" (work in progress with unknown length). */
    val chaptersTotal: Int? = null,
    val isComplete: Boolean = false,
    val publishedAt: Long? = null,
    val updatedAt: Long? = null,
    val inLibrary: Boolean = false,
    val addedAt: Long = 0L,
    /** Last time an update check looked at this work; used to skip recently checked ones. */
    val lastCheckedAt: Long? = null,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workId")],
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val workId: String,
    val number: Int,
    val title: String = "",
    val publishedAt: Long? = null,
    /** When the chapter list entry was last written by a refresh; drives the Updates feed. */
    val fetchedAt: Long? = null,
    val isRead: Boolean = false,
    /** 0f..1f scroll progress within the chapter. */
    val progress: Float = 0f,
    val bookmarked: Boolean = false,
    val isDownloaded: Boolean = false,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
)

@Entity(
    tableName = "work_categories",
    primaryKeys = ["workId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class WorkCategoryCrossRefEntity(
    val workId: String,
    val categoryId: Long,
)

@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workId"), Index("lastReadAt")],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workId: String,
    val chapterId: String,
    val lastReadAt: Long,
    val readDurationMs: Long = 0L,
)
