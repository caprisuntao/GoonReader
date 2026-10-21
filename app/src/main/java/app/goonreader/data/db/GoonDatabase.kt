package app.goonreader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 2: bookmarks (PRD 10.9). Schema JSON is written to app/schemas on every build, so a
 * later change arrives as a reviewable diff plus a real [androidx.room.migration.Migration].
 * Never ship a release with destructive migration enabled.
 */
@Database(
    entities = [
        WorkEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        WorkCategoryCrossRefEntity::class,
        HistoryEntity::class,
        BookmarkEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class GoonDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun statsDao(): StatsDao
}

/** Adds the bookmarks table; existing reading state is untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bookmarks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `workId` TEXT NOT NULL,
                `chapterId` TEXT NOT NULL,
                `chapterNumber` INTEGER NOT NULL,
                `chapterTitle` TEXT NOT NULL,
                `progress` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`workId`) REFERENCES `works`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_workId` ON `bookmarks` (`workId`)")
    }
}
