package app.goonreader.domain.model

import java.time.LocalDate

/**
 * One model for both a search blurb and a full work page: the blurb supplies a subset,
 * the work page fills in the rest. Null means "AO3 did not tell us", never "zero".
 *
 * Dates are [LocalDate] on purpose — AO3 publishes date-only values, and turning them into
 * instants here would shift them a day for users east or west of the server's timezone.
 */
data class Work(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val fandoms: List<String> = emptyList(),
    val rating: String? = null,
    val warnings: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val summaryHtml: String? = null,
    val notesHtml: String? = null,
    val language: String? = null,
    val wordCount: Long? = null,
    val chaptersPublished: Int? = null,
    /** Null when AO3 shows "?" for the projected total (work in progress). */
    val chaptersTotal: Int? = null,
    val isComplete: Boolean = false,
    val publishedAt: LocalDate? = null,
    val updatedAt: LocalDate? = null,
    val kudos: Int? = null,
    val hits: Long? = null,
    val bookmarks: Int? = null,
    val commentCount: Int? = null,
)

/** An entry in a work's chapter list. */
data class ChapterRef(
    val id: String,
    val workId: String,
    val number: Int,
    val title: String,
    val publishedAt: LocalDate?,
)

/**
 * Chapter text ready to render. HTML is already sanitised: no scripts, no event handlers,
 * no `javascript:` links. The reader wraps it in its own document.
 */
data class ChapterContent(
    val workId: String,
    val chapterId: String,
    val title: String?,
    val summaryHtml: String?,
    val startNotesHtml: String?,
    val endNotesHtml: String?,
    val bodyHtml: String,
    /** AO3's comment count for this chapter when the page printed it (PRD 9.10). */
    val commentCount: Int? = null,
)

/** One comment on a chapter, already flattened for the list. */
data class Comment(
    val id: String,
    val author: String,
    val postedAt: LocalDate?,
    val bodyHtml: String,
    /** 0 for a top-level comment; nested replies are one deeper each. */
    val depth: Int,
)

/** One page of comments, plus what the list needs to ask for the next one. */
data class CommentPage(
    val comments: List<Comment>,
    val total: Int?,
    val nextPage: Int?,
)

