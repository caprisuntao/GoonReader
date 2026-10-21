package app.goonreader.data.backup

import app.goonreader.data.db.BookmarkDao
import app.goonreader.data.db.BookmarkEntity
import app.goonreader.data.db.CategoryDao
import app.goonreader.data.db.CategoryEntity
import app.goonreader.data.db.ChapterDao
import app.goonreader.data.db.WorkCategoryCrossRefEntity
import app.goonreader.data.db.WorkDao
import app.goonreader.data.download.DownloadStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportResult {
    data class Imported(val works: Int, val chapters: Int) : ImportResult
    data object InvalidFile : ImportResult
}

/**
 * Library export and import (PRD 9.8).
 *
 * Import merges: entries in the file are upserted and nothing already on the device is deleted,
 * so importing an older backup cannot drop the newer library. Categories are matched by name,
 * bookmarks at the same spot are not duplicated, and a chapter is only marked downloaded when
 * its file still exists.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val works: WorkDao,
    private val chapters: ChapterDao,
    private val categories: CategoryDao,
    private val bookmarks: BookmarkDao,
    private val downloads: DownloadStore,
) {

    suspend fun export(): String = withContext(Dispatchers.IO) {
        BackupCodec.encode(
            BackupData(
                works = works.inLibrary(),
                chapters = chapters.forLibrary(),
                categories = categories.all(),
                memberships = categories.allMemberships(),
                bookmarks = bookmarks.all(),
            ),
        )
    }

    suspend fun import(text: String): ImportResult = withContext(Dispatchers.IO) {
        val data = BackupCodec.decode(text) ?: return@withContext ImportResult.InvalidFile

        data.works.forEach { works.upsert(it.copy(inLibrary = true)) }
        data.chapters.forEach { chapter ->
            // A restored chapter is not "new": clearing fetchedAt keeps the Updates feed honest,
            // and the next real check fills it in again.
            chapters.upsert(
                chapter.copy(
                    fetchedAt = null,
                    isDownloaded = downloads.contains(chapter.workId, chapter.id),
                ),
            )
        }

        val categoryIds = HashMap<Long, Long>()
        data.categories.forEach { category ->
            categories.insert(CategoryEntity(name = category.name))
            categories.idByName(category.name)?.let { categoryIds[category.id] = it }
        }
        data.memberships.forEach { membership ->
            val id = categoryIds[membership.categoryId] ?: return@forEach
            categories.assign(WorkCategoryCrossRefEntity(membership.workId, id))
        }

        data.bookmarks.forEach { bookmark ->
            if (bookmarks.countNear(bookmark.workId, bookmark.chapterId, bookmark.progress) == 0) {
                bookmarks.insert(bookmark.copy(id = 0L))
            }
        }

        ImportResult.Imported(works = data.works.size, chapters = data.chapters.size)
    }
}
