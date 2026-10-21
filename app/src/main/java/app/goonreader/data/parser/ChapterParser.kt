package app.goonreader.data.parser

import app.goonreader.domain.model.ChapterContent
import org.jsoup.Jsoup

/**
 * Parses one chapter's text, notes and summary.
 *
 * Works for both shapes AO3 serves: a dedicated `/chapters/<id>` page and a chapter-1
 * document reached by `/works/<id>`. Returns null only when the page has no chapter body at
 * all, which also covers interstitials and error pages.
 */
object ChapterParser {

    fun parse(html: String, workId: String, chapterId: String): ChapterContent? {
        val doc = Jsoup.parse(html)
        val container = doc.selectFirst(Ao3Selectors.CHAPTER_CONTAINER) ?: return null

        // The chapter summary is a `blockquote.userstuff`, so filter to the div: only the
        // body is a div, on both single- and multi-chapter pages.
        val body = container.select(Ao3Selectors.CHAPTER_BODY).firstOrNull { it.tagName() == "div" } ?: return null

        val notes = container.select(Ao3Selectors.CHAPTER_NOTES)

        return ChapterContent(
            workId = workId,
            chapterId = chapterId,
            title = Ao3Parse.text(container.selectFirst(Ao3Selectors.CHAPTER_TITLE)),
            summaryHtml = container.selectFirst(Ao3Selectors.CHAPTER_SUMMARY)
                ?.let { Ao3Html.contentWithoutLandmarks(it) },
            startNotesHtml = notes.firstOrNull { !it.hasClass("end") }
                ?.let { Ao3Html.contentWithoutLandmarks(it) },
            endNotesHtml = notes.firstOrNull { it.hasClass("end") }
                ?.let { Ao3Html.contentWithoutLandmarks(it) },
            bodyHtml = Ao3Html.contentWithoutLandmarks(body),
            // The count link sits outside #chapters, so it is read from the whole page.
            commentCount = Ao3Parse.parenthesisedCount(
                doc.selectFirst(Ao3Selectors.COMMENT_COUNT_LINK)?.text(),
            ),
        )
    }
}
