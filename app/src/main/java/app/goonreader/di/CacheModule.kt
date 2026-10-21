package app.goonreader.di

import android.content.Context
import app.goonreader.data.cache.ChapterCache
import app.goonreader.data.download.DownloadStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun provideChapterCache(@ApplicationContext context: Context): ChapterCache =
        ChapterCache(File(context.cacheDir, "chapters"))

    /** Downloads live under filesDir: the OS may clear cacheDir, and these must survive. */
    @Provides
    @Singleton
    fun provideDownloadStore(@ApplicationContext context: Context): DownloadStore =
        DownloadStore(File(context.filesDir, "downloads"))
}
