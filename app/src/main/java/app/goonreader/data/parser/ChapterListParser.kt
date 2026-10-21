package app.goonreader.data.parser

import app.goonreader.domain.model.ChapterRef
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Parses `/works/<id>/navigate` into the full chapter list.
 *
 * This page is the only place that lists every chapter id for a work, so update checks
 * depend on it. Single-chapter works have no index at all and legitimately return an empty
 * list; callers fall back to the work page in that case.
 */
object ChapterListParser {

    fun parse(html: String, workId: String): List<ChapterRef> = parse(Jsoup.parse(html), workId)

    fun parse(doc: Document, workId: String): List<ChapterRef> =
        doc.select(Ao3Selectors.CHAPTER_INDEX_ITEM).mapNotNull { item ->
            val link = item.selectFirst(Ao3Selectors.CHAPTER_INDEX_LINK) ?: return@mapNotNull null
            val chapterId = Ao3Selectors.chapterIdFromHref(link.attr("href")) ?: return@mapNotNull null
            // Link text is "12. Chapter title"; the number is the part before the first dot.
            val label = link.text().trim()
            val number = label.substringBefore('.').trim().toIntOrNull() ?: return@mapNotNull null
            ChapterRef(
                id = chapterId,
                workId = workId,
                number = number,
                title = label.substringAfter('.', missingDelimiterValue = "").trim(),
                publishedAt = Ao3Parse.parenthesisedDate(
                    Ao3Parse.text(item.selectFirst(Ao3Selectors.CHAPTER_INDEX_DATE)),
                ),
            )
        }
}
