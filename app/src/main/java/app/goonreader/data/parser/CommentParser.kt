package app.goonreader.data.parser

import app.goonreader.domain.model.Comment
import app.goonreader.domain.model.CommentPage
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.LocalDate

/**
 * Parses a chapter's comments, requested with `show_comments=true` (PRD 9.10).
 *
 * AO3 nests replies as `li.comment.group` inside another `ol.thread`; depth is therefore the
 * number of thread lists above a comment, not a class name. The page also prints the total and
 * a `rel=next` pagination link, so one request is all the UI needs per page.
 */
object CommentParser {

    fun parse(html: String): CommentPage = parse(Jsoup.parse(html))

    fun parse(doc: Document): CommentPage {
        val comments = doc.select(Ao3Selectors.COMMENT).mapNotNull { element -> toComment(element) }
        return CommentPage(
            comments = comments,
            total = Ao3Parse.parenthesisedCount(
                doc.selectFirst(Ao3Selectors.COMMENT_COUNT_LINK)?.text(),
            ),
            nextPage = nextPage(doc),
        )
    }

    private fun toComment(element: Element): Comment? {
        val id = element.id().removePrefix("comment_").takeIf { it.isNotEmpty() } ?: return null
        val byline = element.selectFirst(Ao3Selectors.COMMENT_BYLINE)
        val body = element.selectFirst(Ao3Selectors.COMMENT_BODY) ?: return null

        return Comment(
            id = id,
            author = author(byline),
            postedAt = postedAt(byline),
            bodyHtml = Ao3Html.contentWithoutLandmarks(body),
            depth = depth(element),
        )
    }

    /** A linked pseud, or the byline's own text ("Account Deleted", "Guest"). */
    private fun author(byline: Element?): String =
        byline?.selectFirst(Ao3Selectors.COMMENT_AUTHOR_LINK)?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: byline?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Anonymous"

    /** The byline prints "02" + "Sep" + "2015" in separate elements. */
    private fun postedAt(byline: Element?): LocalDate? {
        val posted = byline?.selectFirst(Ao3Selectors.COMMENT_POSTED) ?: return null
        val day = posted.selectFirst(Ao3Selectors.COMMENT_DAY)?.text()?.trim()
        val month = posted.selectFirst(Ao3Selectors.COMMENT_MONTH)?.text()?.trim()
        val year = posted.selectFirst(Ao3Selectors.COMMENT_YEAR)?.text()?.trim()
        return Ao3Parse.shortDate(listOfNotNull(day, month, year).joinToString(" "))
    }

    private fun depth(element: Element): Int =
        (element.parents().count { it.tagName() == "ol" && it.hasClass("thread") } - 1).coerceAtLeast(0)

    private fun nextPage(doc: Document): Int? {
        val next = doc.selectFirst("${Ao3Selectors.COMMENT_PAGINATION} a[rel=next]") ?: return null
        return next.attr("href").toHttpUrlOrNull()?.queryParameter("page")?.toIntOrNull()
    }
}
