package app.goonreader.data.parser

import app.goonreader.domain.model.Work
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Parses a work page. The work id is passed in rather than guessed: AO3 does not put the id
 * in a canonical link, and every caller already knows which work it just fetched.
 *
 * For open works `/works/<id>` redirects to chapter 1, so this parser and [ChapterParser]
 * routinely run against the same document.
 */
object WorkParser {

    fun parse(html: String, workId: String): Work? = parse(Jsoup.parse(html), workId)

    fun parse(doc: Document, workId: String): Work? {
        val title = Ao3Parse.text(doc.selectFirst(Ao3Selectors.WORK_TITLE)) ?: return null

        val meta = doc.selectFirst(Ao3Selectors.WORK_META)
        val stats = doc.selectFirst(Ao3Selectors.STATS)
        val preface = doc.selectFirst(Ao3Selectors.WORK_PREFACE)
        val (published, total) = Ao3Parse.chapters(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_CHAPTERS)))

        // "Completed:" vs "Updated:" is the only explicit completion flag on the page.
        val statusLabel = Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_STATUS_LABEL)).orEmpty()
        val isComplete = statusLabel.contains("Completed", ignoreCase = true) ||
            (published != null && published > 0 && published == total)

        return Work(
            id = workId,
            title = title,
            authors = Ao3Parse.tags(doc, Ao3Selectors.WORK_AUTHOR),
            fandoms = Ao3Parse.tags(meta, Ao3Selectors.META_FANDOM),
            rating = Ao3Parse.tags(meta, Ao3Selectors.META_RATING).firstOrNull(),
            warnings = Ao3Parse.tags(meta, Ao3Selectors.META_WARNING),
            categories = Ao3Parse.tags(meta, Ao3Selectors.META_CATEGORY),
            relationships = Ao3Parse.tags(meta, Ao3Selectors.META_RELATIONSHIP),
            characters = Ao3Parse.tags(meta, Ao3Selectors.META_CHARACTER),
            tags = Ao3Parse.tags(meta, Ao3Selectors.META_FREEFORM),
            summaryHtml = preface?.selectFirst(Ao3Selectors.WORK_SUMMARY)?.let { Ao3Html.contentWithoutLandmarks(it) },
            notesHtml = preface?.selectFirst(Ao3Selectors.WORK_NOTES)?.let { Ao3Html.contentWithoutLandmarks(it) },
            wordCount = Ao3Parse.longCount(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_WORDS))),
            chaptersPublished = published,
            chaptersTotal = total,
            isComplete = isComplete,
            publishedAt = Ao3Parse.isoDate(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_PUBLISHED))),
            updatedAt = Ao3Parse.isoDate(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_STATUS))),
            kudos = Ao3Parse.count(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_KUDOS))),
            hits = Ao3Parse.longCount(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_HITS))),
            bookmarks = Ao3Parse.count(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_BOOKMARKS))),
            commentCount = Ao3Parse.count(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_COMMENTS))),
        )
    }
}
