package app.goonreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {

    @Query("SELECT * FROM works WHERE inLibrary = 1 ORDER BY updatedAt DESC")
    fun observeLibrary(): Flow<List<WorkEntity>>

    @Query("SELECT COUNT(*) FROM works WHERE inLibrary = 1")
    fun observeLibraryCount(): Flow<Int>

    @Query("SELECT * FROM works WHERE id = :workId")
    suspend fun getById(workId: String): WorkEntity?

    @Query("SELECT * FROM works WHERE id = :workId")
    fun observeById(workId: String): Flow<WorkEntity?>

    /** Works due for an update check: in the library and not checked since [since]. */
    @Query(
        "SELECT * FROM works WHERE inLibrary = 1 AND (lastCheckedAt IS NULL OR lastCheckedAt < :since) " +
            "ORDER BY lastCheckedAt IS NOT NULL, lastCheckedAt ASC LIMIT :limit",
    )
    suspend fun worksDueForCheck(since: Long, limit: Int): List<WorkEntity>

    @Upsert
    suspend fun upsert(work: WorkEntity)

    @Query("UPDATE works SET inLibrary = 0 WHERE id = :workId")
    suspend fun removeFromLibrary(workId: String)

    @Query("DELETE FROM works WHERE id = :workId")
    suspend fun delete(workId: String)
}

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE workId = :workId ORDER BY number ASC")
    fun observeForWork(workId: String): Flow<List<ChapterEntity>>

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

    /** Chapters that appeared recently, for the Updates tab. */
    @Query(
        "SELECT * FROM chapters WHERE fetchedAt IS NOT NULL AND fetchedAt >= :since " +
            "ORDER BY fetchedAt DESC LIMIT :limit",
    )
    fun observeRecentlyFetched(since: Long, limit: Int): Flow<List<ChapterEntity>>
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY lastReadAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}
