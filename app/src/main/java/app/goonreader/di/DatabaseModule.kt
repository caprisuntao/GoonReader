package app.goonreader.di

import android.content.Context
import androidx.room.Room
import app.goonreader.data.db.BookmarkDao
import app.goonreader.data.db.ChapterDao
import app.goonreader.data.db.CategoryDao
import app.goonreader.data.db.GoonDatabase
import app.goonreader.data.db.HistoryDao
import app.goonreader.data.db.MIGRATION_1_2
import app.goonreader.data.db.StatsDao
import app.goonreader.data.db.WorkDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GoonDatabase =
        Room.databaseBuilder(context, GoonDatabase::class.java, "goonreader.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideWorkDao(db: GoonDatabase): WorkDao = db.workDao()

    @Provides
    fun provideChapterDao(db: GoonDatabase): ChapterDao = db.chapterDao()

    @Provides
    fun provideCategoryDao(db: GoonDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideHistoryDao(db: GoonDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideBookmarkDao(db: GoonDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideStatsDao(db: GoonDatabase): StatsDao = db.statsDao()
}
