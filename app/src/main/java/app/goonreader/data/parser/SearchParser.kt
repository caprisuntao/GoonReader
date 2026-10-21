package app.goonreader.data.parser

import app.goonreader.domain.model.Work
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses search and tag-browse result pages into [Work] blurbs.
 *
 * A blurb carries work metadata only; chapter text is never on this page.
 */
object SearchParser {

    fun parse(html: String): List<Work> = parse(Jsoup.parse(html))

    fun parse(doc: Document): List<Work> =
        doc.select(Ao3Selectors.SEARCH_RESULT).mapNotNull { blurb -> toWork(blurb) }

    private fun toWork(blurb: Element): Work? {
        val titleLink = blurb.selectFirst(Ao3Selectors.BLURB_TITLE_LINK) ?: return null
        val id = blurb.id().removePrefix("work_").ifEmpty { null }
            ?: Ao3Selectors.workIdFromHref(titleLink.attr("href"))
            ?: return null

        val stats = blurb.selectFirst(Ao3Selectors.STATS)
        val (published, total) = Ao3Parse.chapters(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_CHAPTERS)))

        return Work(
            id = id,
            title = titleLink.text().trim(),
            authors = Ao3Parse.tags(blurb, Ao3Selectors.BLURB_AUTHOR),
            fandoms = Ao3Parse.tags(blurb, Ao3Selectors.BLURB_FANDOM),
            rating = Ao3Parse.tags(blurb, Ao3Selectors.BLURB_RATING).firstOrNull(),
            warnings = blurb.select(Ao3Selectors.BLURB_WARNING).map { it.attr("title") }.filter { it.isNotBlank() },
            categories = blurb.select(Ao3Selectors.BLURB_CATEGORY).map { it.attr("title") }.filter { it.isNotBlank() },
            relationships = Ao3Parse.blurbTags(blurb, Ao3Selectors.TAG_GROUP_RELATIONSHIP),
            characters = Ao3Parse.blurbTags(blurb, Ao3Selectors.TAG_GROUP_CHARACTER),
            tags = Ao3Parse.blurbTags(blurb, Ao3Selectors.TAG_GROUP_FREEFORM),
            summaryHtml = blurb.selectFirst(Ao3Selectors.BLURB_SUMMARY)?.let { Ao3Html.contentWithoutLandmarks(it) },
            language = Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_LANGUAGE)),
            wordCount = Ao3Parse.longCount(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_WORDS))),
            chaptersPublished = published,
            chaptersTotal = total,
            // "complete-yes" badges mark finished works; anything else is a WIP.
            isComplete = blurb.selectFirst(Ao3Selectors.BLURB_WIP)?.hasClass("complete-yes") == true,
            updatedAt = Ao3Parse.shortDate(Ao3Parse.text(blurb.selectFirst(Ao3Selectors.BLURB_DATE))),
            kudos = Ao3Parse.count(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_KUDOS))),
            hits = Ao3Parse.longCount(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_HITS))),
            bookmarks = Ao3Parse.count(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_BOOKMARKS))),
            commentCount = Ao3Parse.count(Ao3Parse.text(stats?.selectFirst(Ao3Selectors.STAT_COMMENTS))),
        )
    }
}
