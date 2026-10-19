package app.goonreader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Version 1. Schema JSON is written to app/schemas on every build, so a later change
 * arrives as a reviewable diff plus a real [androidx.room.migration.Migration].
 * Never ship a release with destructive migration enabled.
 */
@Database(
    entities = [
        WorkEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        WorkCategoryCrossRefEntity::class,
        HistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class GoonDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun historyDao(): HistoryDao
}
