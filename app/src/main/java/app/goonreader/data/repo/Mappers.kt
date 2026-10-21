package app.goonreader.data.repo

import app.goonreader.data.db.ChapterEntity
import app.goonreader.data.db.WorkEntity
import app.goonreader.domain.model.ChapterRef
import app.goonreader.domain.model.Work
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

internal fun LocalDate.toEpochMillis(): Long = toEpochDay() * MILLIS_PER_DAY

internal fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this / MILLIS_PER_DAY)

/**
 * Entity -> domain. Multi-valued fields are newline-separated in the DB; blank entries are
 * dropped so an empty column reads as an empty list rather than `[""]`.
 */
fun WorkEntity.toDomain(): Work = Work(
    id = id,
    title = title,
    authors = lines(authors),
    fandoms = lines(fandoms),
    rating = rating.ifBlank { null },
    warnings = lines(warnings),
    categories = lines(categories),
    relationships = lines(relationships),
    characters = lines(characters),
    tags = lines(tags),
    summaryHtml = summary.ifBlank { null },
    notesHtml = null,
    language = language.ifBlank { null },
    wordCount = wordCount,
    chaptersPublished = chaptersPublished,
    chaptersTotal = chaptersTotal,
    isComplete = isComplete,
    publishedAt = publishedAt?.toLocalDate(),
    updatedAt = updatedAt?.toLocalDate(),
    kudos = kudos,
    hits = hits,
    bookmarks = bookmarks,
    commentCount = commentCount,
)

/** Domain -> entity. [existing] preserves `addedAt` when refreshing a work already in the library. */
fun Work.toEntity(existing: WorkEntity?, inLibrary: Boolean, addedAt: Long): WorkEntity = WorkEntity(
    id = id,
    title = title,
    authors = authors.joinToString("\n"),
    fandoms = fandoms.joinToString("\n"),
    rating = rating.orEmpty(),
    warnings = warnings.joinToString("\n"),
    categories = categories.joinToString("\n"),
    relationships = relationships.joinToString("\n"),
    characters = characters.joinToString("\n"),
    tags = tags.joinToString("\n"),
    summary = summaryHtml.orEmpty(),
    language = language.orEmpty(),
    wordCount = wordCount ?: 0L,
    chaptersPublished = chaptersPublished ?: 0,
    chaptersTotal = chaptersTotal,
    isComplete = isComplete,
    publishedAt = publishedAt?.toEpochMillis(),
    updatedAt = updatedAt?.toEpochMillis(),
    inLibrary = inLibrary,
    addedAt = existing?.addedAt ?: addedAt,
    lastCheckedAt = existing?.lastCheckedAt,
    kudos = kudos,
    hits = hits,
    bookmarks = bookmarks,
    commentCount = commentCount,
)

/**
 * Reconciles a freshly parsed chapter list with what is stored: read state, progress and
 * download flags belong to the user and must survive a refresh.
 *
 * [markNewAsFetched] stamps chapters that did not exist before, which is what the Updates feed
 * reads. Initial library adds pass false so old chapters do not appear as new.
 */
internal fun mergeChapters(
    existing: List<ChapterEntity>,
    incoming: List<ChapterRef>,
    markNewAsFetched: Boolean,
    now: Long,
): List<ChapterEntity> {
    val byId = existing.associateBy { it.id }
    return incoming.map { ref ->
        val old = byId[ref.id]
        ChapterEntity(
            id = ref.id,
            workId = ref.workId,
            number = ref.number,
            title = ref.title,
            publishedAt = ref.publishedAt?.toEpochMillis(),
            // Only chapters that were absent before count as new. Keying this off the stored
            // timestamp instead would re-stamp every chapter on the first refresh, because
            // initial adds deliberately leave fetchedAt empty.
            fetchedAt = if (old != null) old.fetchedAt else if (markNewAsFetched) now else null,
            isRead = old?.isRead ?: false,
            progress = old?.progress ?: 0f,
            bookmarked = old?.bookmarked ?: false,
            isDownloaded = old?.isDownloaded ?: false,
        )
    }
}

private fun lines(joined: String): List<String> =
    joined.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
