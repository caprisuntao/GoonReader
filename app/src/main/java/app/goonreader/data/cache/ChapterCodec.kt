package app.goonreader.data.cache

import app.goonreader.domain.model.ChapterContent
import org.jsoup.Jsoup

/**
 * Chapters are cached as HTML, not JSON.
 *
 * The payload already is HTML, it gzips well, and decoding costs one Jsoup parse with no extra
 * dependency. The shape below is internal to the cache: the reader never sees it, and the
 * sanitised fragments are embedded unchanged.
 */
object ChapterCodec {

    fun encode(content: ChapterContent): String {
        val article = Jsoup.parse("<html><body></body></html>")
            .body()
            .appendElement("article")
            .attr("data-work", content.workId)
            .attr("data-chapter", content.chapterId)
        content.commentCount?.let { article.attr("data-comments", it.toString()) }

        content.title?.takeIf { it.isNotBlank() }
            ?.let { article.appendElement("h1").addClass("chapter-title").text(it) }
        content.summaryHtml?.takeIf { it.isNotBlank() }
            ?.let { article.appendElement("section").addClass("summary").html(it) }
        content.startNotesHtml?.takeIf { it.isNotBlank() }
            ?.let { article.appendElement("section").addClass("notes-start").html(it) }
        article.appendElement("div").addClass("body").html(content.bodyHtml)
        content.endNotesHtml?.takeIf { it.isNotBlank() }
            ?.let { article.appendElement("section").addClass("notes-end").html(it) }

        return article.outerHtml()
    }

    /** Null when the cached document is not a chapter (corrupt entry, older format). */
    fun decode(html: String, workId: String, chapterId: String): ChapterContent? {
        val article = Jsoup.parse(html).selectFirst("article") ?: return null
        val body = article.selectFirst("div.body")?.html()?.takeIf { it.isNotBlank() } ?: return null

        return ChapterContent(
            workId = workId,
            chapterId = chapterId,
            title = article.selectFirst("h1.chapter-title")?.text()?.takeIf { it.isNotBlank() },
            summaryHtml = article.selectFirst("section.summary")?.html()?.takeIf { it.isNotBlank() },
            startNotesHtml = article.selectFirst("section.notes-start")?.html()?.takeIf { it.isNotBlank() },
            endNotesHtml = article.selectFirst("section.notes-end")?.html()?.takeIf { it.isNotBlank() },
            bodyHtml = body,
            commentCount = article.attr("data-comments").toIntOrNull(),
        )
    }
}
