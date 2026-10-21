package app.goonreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {

    /**
     * Library list with read progress, so a row never needs a second query per work.
     * One query serves all three filters: a category tab, and Downloaded only.
     */
    @Query(
        """
        SELECT w.*,
            (SELECT COUNT(*) FROM chapters c WHERE c.workId = w.id AND c.isRead = 1) AS readChapters,
            (SELECT COUNT(*) FROM chapters c WHERE c.workId = w.id) AS chapterCount
        FROM works w
        WHERE w.inLibrary = 1
          AND (:categoryId IS NULL OR EXISTS (
                SELECT 1 FROM work_categories wc WHERE wc.workId = w.id AND wc.categoryId = :categoryId))
          AND (:downloadedOnly = 0 OR EXISTS (
                SELECT 1 FROM chapters d WHERE d.workId = w.id AND d.isDownloaded = 1))
        ORDER BY w.updatedAt DESC
        """,
    )
    fun observeLibraryRows(categoryId: Long?, downloadedOnly: Boolean): Flow<List<LibraryRow>>

    @Query("SELECT COUNT(*) FROM works WHERE inLibrary = 1")
    fun observeLibraryCount(): Flow<Int>

    /** Ids only: browse results mark rows that are already in the library. */
    @Query("SELECT id FROM works WHERE inLibrary = 1")
    fun observeLibraryIds(): Flow<List<String>>

    /** Drives "Library last updated: X ago" on the Updates tab. */
    @Query("SELECT MAX(lastCheckedAt) FROM works WHERE inLibrary = 1")
    fun observeLastCheckedAt(): Flow<Long?>

    @Query("SELECT * FROM works WHERE id = :workId")
    suspend fun getById(workId: String): WorkEntity?

    /** Works due for an update check: in the library and not checked since [since]. */
    @Query(
        "SELECT * FROM works WHERE inLibrary = 1 AND (lastCheckedAt IS NULL OR lastCheckedAt < :since) " +
            "ORDER BY lastCheckedAt IS NOT NULL, lastCheckedAt ASC LIMIT :limit",
    )
    suspend fun worksDueForCheck(since: Long, limit: Int): List<WorkEntity>

    @Upsert
    suspend fun upsert(work: WorkEntity)

    @Query("DELETE FROM works WHERE id = :workId")
    suspend fun delete(workId: String)

    /** Backup export: the library only, since preview works are not the user's data. */
    @Query("SELECT * FROM works WHERE inLibrary = 1 ORDER BY id")
    suspend fun inLibrary(): List<WorkEntity>
}

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE workId = :workId ORDER BY number ASC")
    suspend fun getForWork(workId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getById(chapterId: String): ChapterEntity?

    @Upsert
    suspend fun upsert(chapter: ChapterEntity)

    @Upsert
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    @Query("UPDATE chapters SET isRead = :read WHERE id = :chapterId")
    suspend fun setRead(chapterId: String, read: Boolean)

    @Query("UPDATE chapters SET progress = :progress WHERE id = :chapterId")
    suspend fun setProgress(chapterId: String, progress: Float)

    @Query("UPDATE chapters SET isDownloaded = :downloaded WHERE id = :chapterId")
    suspend fun setDownloaded(chapterId: String, downloaded: Boolean)

    @Query("UPDATE chapters SET isDownloaded = 0")
    suspend fun clearDownloadFlags()

    /** Chapters that appeared recently, for the Updates tab, with the owning work's title. */
    @Query(
        """
        SELECT c.*, w.title AS workTitle
        FROM chapters c
        INNER JOIN works w ON w.id = c.workId
        WHERE w.inLibrary = 1 AND c.fetchedAt IS NOT NULL
        ORDER BY c.fetchedAt DESC
        LIMIT :limit
        """,
    )
    fun observeUpdates(limit: Int): Flow<List<UpdateRow>>

    /** Backup export: every chapter of every library work. */
    @Query(
        """
        SELECT c.* FROM chapters c
        INNER JOIN works w ON w.id = c.workId
        WHERE w.inLibrary = 1
        ORDER BY c.workId, c.number
        """,
    )
    suspend fun forLibrary(): List<ChapterEntity>
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    /** With library counts, for the Categories screen. */
    @Query(
        """
        SELECT c.*, (
            SELECT COUNT(*) FROM work_categories wc
            INNER JOIN works w ON w.id = wc.workId AND w.inLibrary = 1
            WHERE wc.categoryId = c.id
        ) AS workCount
        FROM categories c
        ORDER BY c.name ASC
        """,
    )
    fun observeWithCounts(): Flow<List<CategoryRow>>

    @Query("SELECT categoryId FROM work_categories WHERE workId = :workId")
    fun observeCategoryIds(workId: String): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assign(ref: WorkCategoryCrossRefEntity)

    @Query("DELETE FROM work_categories WHERE workId = :workId AND categoryId = :categoryId")
    suspend fun unassign(workId: String, categoryId: Long)

    /** Backup: categories are restored by name, because their ids are local to a database. */
    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM work_categories")
    suspend fun allMemberships(): List<WorkCategoryCrossRefEntity>

    @Query("SELECT id FROM categories WHERE name = :name")
    suspend fun idByName(name: String): Long?
}

@Dao
interface HistoryDao {

    /** Newest first, with work title and chapter label for the list (PRD 9.4). */
    @Query(
        """
        SELECT h.*, w.title AS workTitle, c.number AS chapterNumber, c.title AS chapterTitle
        FROM history h
        INNER JOIN works w ON w.id = h.workId
        LEFT JOIN chapters c ON c.id = h.chapterId
        ORDER BY h.lastReadAt DESC
        LIMIT :limit
        """,
    )
    fun observeRows(limit: Int): Flow<List<HistoryRow>>

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    /** Updates the session that is still open; one row per reading session. */
    @Query("UPDATE history SET lastReadAt = :readAt, readDurationMs = :durationMs WHERE id = :id")
    suspend fun touch(id: Long, readAt: Long, durationMs: Long)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE workId = :workId ORDER BY chapterNumber ASC, progress ASC")
    fun observeForWork(workId: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM bookmarks ORDER BY id")
    suspend fun all(): List<BookmarkEntity>

    /** Import skips a bookmark that already exists at the same spot. */
    @Query(
        "SELECT COUNT(*) FROM bookmarks WHERE workId = :workId AND chapterId = :chapterId " +
            "AND ABS(progress - :progress) < 0.02",
    )
    suspend fun countNear(workId: String, chapterId: String, progress: Float): Int
}

@Dao
interface StatsDao {

    /** Everything the Statistics screen shows, in one query. */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM works WHERE inLibrary = 1) AS works,
            (SELECT COUNT(*) FROM chapters c INNER JOIN works w ON w.id = c.workId
                WHERE c.isRead = 1 AND w.inLibrary = 1) AS chaptersRead,
            (SELECT COALESCE(SUM(readDurationMs), 0) FROM history) AS readingMillis,
            (SELECT COUNT(*) FROM bookmarks) AS bookmarks,
            (SELECT COUNT(*) FROM chapters WHERE isDownloaded = 1) AS downloaded
        """,
    )
    fun observe(): Flow<StatsRow>
}
