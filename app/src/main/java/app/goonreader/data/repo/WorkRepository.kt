package app.goonreader.data.repo

import androidx.room.withTransaction
import app.goonreader.data.cache.ChapterCache
import app.goonreader.data.db.ChapterDao
import app.goonreader.data.db.ChapterEntity
import app.goonreader.data.db.GoonDatabase
import app.goonreader.data.db.LibraryRow
import app.goonreader.data.db.UpdateRow
import app.goonreader.data.db.WorkDao
import app.goonreader.data.db.WorkEntity
import app.goonreader.data.download.DownloadStore
import app.goonreader.data.network.Ao3Client
import app.goonreader.data.network.Ao3Page
import app.goonreader.data.network.Ao3Urls
import app.goonreader.data.parser.ChapterListParser
import app.goonreader.data.parser.WorkParser
import app.goonreader.domain.model.ChapterRef
import app.goonreader.domain.model.Work
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Repository outcome. Failures carry a reason the UI can act on, not an exception. */
sealed interface RepoResult<out T> {
    data class Ok<T>(val value: T) : RepoResult<T>
    data class Failed(val reason: FailureReason) : RepoResult<Nothing>
}

enum class FailureReason {
    NotFound,
    /** Cloudflare wants a real browser; only the user can clear this. */
    ChallengeRequired,
    Offline,
    HttpError,
    ParseError,
}

/** Shared mapping so every repository reports a fetch the same way. */
internal fun Ao3Page.toFailureReason(): FailureReason = when (this) {
    Ao3Page.NotFound -> FailureReason.NotFound
    Ao3Page.ChallengeRequired -> FailureReason.ChallengeRequired
    is Ao3Page.NetworkError -> FailureReason.Offline
    is Ao3Page.HttpError -> FailureReason.HttpError
    // Unreachable: callers only map non-success pages. Keeps the `when` exhaustive.
    is Ao3Page.Success -> FailureReason.ParseError
}

/** What one work fetch produced, before anything is written to the database. */
data class WorkBundle(val work: Work, val chapters: List<ChapterRef>)

/** What one update check found for one work. */
sealed interface UpdateOutcome {
    data object NoChange : UpdateOutcome
    data object Failed : UpdateOutcome

    /** New chapters were added; their ids feed auto-download (PRD 9.8). */
    data class Updated(val newChapterIds: List<String>) : UpdateOutcome
}

/**
 * The only place that turns network + HTML into stored state.
 *
 * Library membership is a flag on the work row: previewing a work must not write anything
 * (PRD 4), which is why [fetchWork] is read-only and only [addToLibrary] persists.
 */
@Singleton
class WorkRepository @Inject constructor(
    private val client: Ao3Client,
    private val workDao: WorkDao,
    private val chapterDao: ChapterDao,
    private val chapterCache: ChapterCache,
    private val downloadStore: DownloadStore,
    private val db: GoonDatabase,
) {

    fun observeLibrary(categoryId: Long? = null, downloadedOnly: Boolean = false): Flow<List<LibraryRow>> =
        workDao.observeLibraryRows(categoryId, downloadedOnly)

    fun observeUpdates(limit: Int = UPDATES_LIMIT): Flow<List<UpdateRow>> = chapterDao.observeUpdates(limit)

    fun observeLastCheckedAt(): Flow<Long?> = workDao.observeLastCheckedAt()

    suspend fun getWork(workId: String): WorkEntity? = workDao.getById(workId)

    suspend fun getChapters(workId: String): List<ChapterEntity> = chapterDao.getForWork(workId)

    suspend fun worksDueForCheck(since: Long, limit: Int): List<WorkEntity> =
        workDao.worksDueForCheck(since, limit)

    /** Fetch and parse a work without persisting it. */
    suspend fun fetchWork(workId: String, background: Boolean = false): RepoResult<WorkBundle> {
        val page = client.get(Ao3Urls.work(workId), background)
        if (page !is Ao3Page.Success) return RepoResult.Failed(page.toFailureReason())

        val work = WorkParser.parse(page.html, workId)
            ?: return RepoResult.Failed(FailureReason.ParseError)

        return when (val chapters = fetchChapters(work, background)) {
            is RepoResult.Ok -> RepoResult.Ok(WorkBundle(work, chapters.value))
            is RepoResult.Failed -> chapters
        }
    }

    suspend fun addToLibrary(bundle: WorkBundle) {
        val existingWork = workDao.getById(bundle.work.id)
        val existingChapters = chapterDao.getForWork(bundle.work.id)
        val now = System.currentTimeMillis()
        // Just fetched, so it counts as checked; otherwise the next background run rechecks it.
        val entity = bundle.work
            .toEntity(existingWork, inLibrary = true, addedAt = now)
            .copy(lastCheckedAt = now)
        // First add: nothing may show up in Updates, so no fetchedAt stamps.
        val chapters = mergeChapters(existingChapters, bundle.chapters, markNewAsFetched = false, now = 0L)

        db.withTransaction {
            workDao.upsert(entity)
            chapterDao.upsertAll(chapters)
        }
    }

    suspend fun removeFromLibrary(workId: String) {
        val chapters = chapterDao.getForWork(workId)
        // Cascade deletes chapters; a removed work is gone, not merely hidden.
        workDao.delete(workId)
        // Its text goes with it: leaving files behind would be invisible storage, and a re-add
        // would then serve an old copy as if it were current.
        chapters.forEach { chapter ->
            downloadStore.remove(workId, chapter.id)
            chapterCache.remove(workId, chapter.id)
        }
    }

    /**
     * Manual "Refresh work" (PRD 7.4): re-fetch the work and its whole chapter list, preserving
     * read state and flagging genuinely new chapters. Cached text is dropped because an author
     * can edit an existing chapter without changing the count; downloads are the user's copy and
     * stay untouched.
     */
    suspend fun refresh(workId: String): RepoResult<Unit> {
        val fetched = fetchWork(workId)
        if (fetched is RepoResult.Failed) return fetched

        val bundle = (fetched as RepoResult.Ok).value
        val existingWork = workDao.getById(workId)
        if (existingWork?.inLibrary == true) {
            val now = System.currentTimeMillis()
            val existingChapters = chapterDao.getForWork(workId)
            val written = writeIfStillInLibrary(workId) {
                workDao.upsert(
                    bundle.work
                        .toEntity(existingWork, inLibrary = true, addedAt = existingWork.addedAt)
                        .copy(lastCheckedAt = now),
                )
                chapterDao.upsertAll(
                    mergeChapters(existingChapters, bundle.chapters, markNewAsFetched = true, now = now),
                )
            }
            if (written != null) existingChapters.forEach { chapterCache.remove(workId, it.id) }
        }
        return RepoResult.Ok(Unit)
    }

    /**
     * Background update detection (PRD 7.4): compare the work page's published count and updated
     * date against what is stored, and only pay for the chapter list when one of them moved.
     * Stats and metadata are refreshed on every check; failures leave `lastCheckedAt` alone so
     * the work is retried instead of silently skipped.
     */
    suspend fun checkForUpdate(workId: String, background: Boolean = true): UpdateOutcome {
        val stored = workDao.getById(workId) ?: return UpdateOutcome.Failed
        val page = client.get(Ao3Urls.work(workId), background)
        if (page !is Ao3Page.Success) return UpdateOutcome.Failed

        val fresh = WorkParser.parse(page.html, workId) ?: return UpdateOutcome.Failed
        val now = System.currentTimeMillis()

        if (!hasNewChapterData(stored, fresh)) {
            val written = writeIfStillInLibrary(workId) {
                workDao.upsert(
                    fresh.toEntity(stored, inLibrary = true, addedAt = stored.addedAt).copy(lastCheckedAt = now),
                )
            }
            return if (written != null) UpdateOutcome.NoChange else UpdateOutcome.Failed
        }

        val chapterRefs = when (val chapters = fetchChapters(fresh, background)) {
            is RepoResult.Ok -> chapters.value
            is RepoResult.Failed -> return UpdateOutcome.Failed
        }
        val existingChapters = chapterDao.getForWork(workId)
        val existingIds = existingChapters.mapTo(HashSet()) { it.id }
        val newChapterIds = chapterRefs.map { it.id }.filter { it !in existingIds }
        val written = writeIfStillInLibrary(workId) {
            workDao.upsert(
                fresh.toEntity(stored, inLibrary = true, addedAt = stored.addedAt).copy(lastCheckedAt = now),
            )
            chapterDao.upsertAll(
                mergeChapters(existingChapters, chapterRefs, markNewAsFetched = true, now = now),
            )
        }
        return if (written != null) UpdateOutcome.Updated(newChapterIds) else UpdateOutcome.Failed
    }

    /**
     * Writes only while the work is still in the library. A check can take ten seconds; without
     * this, removing the work mid-check would have the check write it straight back.
     */
    private suspend fun <T> writeIfStillInLibrary(workId: String, write: suspend () -> T): T? =
        db.withTransaction {
            if (workDao.getById(workId)?.inLibrary == true) write() else null
        }

    private suspend fun fetchChapters(work: Work, background: Boolean): RepoResult<List<ChapterRef>> {
        val nav = client.get(Ao3Urls.navigate(work.id), background)
        if (nav is Ao3Page.Success) {
            val parsed = ChapterListParser.parse(nav.html, work.id)
            // A one-shot's /navigate redirects back to the work page, so an empty list is
            // expected there rather than an error.
            return RepoResult.Ok(parsed.ifEmpty { listOf(singleChapter(work)) })
        }
        // A multi-chapter work cannot be enumerated without its index page.
        return if ((work.chaptersPublished ?: 1) > 1) {
            RepoResult.Failed(nav.toFailureReason())
        } else {
            RepoResult.Ok(listOf(singleChapter(work)))
        }
    }

    /** One-shots have no chapter index; AO3 uses the work id as the chapter id. */
    private fun singleChapter(work: Work) = ChapterRef(
        id = work.id,
        workId = work.id,
        number = 1,
        title = work.title,
        publishedAt = work.publishedAt,
    )

    private companion object {
        /** The feed is a recent-activity list, not an archive; older rows are not worth loading. */
        const val UPDATES_LIMIT = 200
    }
}

/**
 * Did a work gain a chapter since it was stored? AO3's published count is authoritative; the
 * updated date catches the count staying put (author edits), and is compared in the same
 * date-only unit the parser reads so timezone never shifts the answer.
 */
internal fun hasNewChapterData(stored: WorkEntity, fresh: Work): Boolean {
    val published = fresh.chaptersPublished ?: stored.chaptersPublished
    if (published > stored.chaptersPublished) return true

    val updated = fresh.updatedAt?.toEpochMillis() ?: return false
    return stored.updatedAt != null && updated > stored.updatedAt
}
