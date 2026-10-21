package app.goonreader.data.parser

import app.goonreader.domain.model.TagRef
import app.goonreader.domain.model.TagType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses AO3's tag search into [TagRef]s.
 *
 * Rows read like `Freeform: <a class="tag" href="/tags/...">name</a> (12)`. The reply for a
 * search is a list of tags, not works: the app then follows the `href` to that tag's works.
 */
object TagSearchParser {

    private val TYPE = Regex("^\\s*([A-Za-z]+)\\s*:")
    private val COUNT = Regex("\\(([\\d,]+)\\)\\s*$")

    fun parse(html: String): List<TagRef> = parse(Jsoup.parse(html))

    fun parse(doc: Document): List<TagRef> =
        doc.select(Ao3Selectors.TAG_SEARCH_RESULT).mapNotNull { row -> toTag(row) }

    private fun toTag(row: Element): TagRef? {
        val link = row.selectFirst(Ao3Selectors.TAG_SEARCH_LINK) ?: return null
        val href = link.attr("href").trim().takeIf { it.isNotEmpty() } ?: return null
        val name = link.text().trim().takeIf { it.isNotEmpty() } ?: return null

        // ownText() of the wrapper is "Freeform:  (12)": the link text sits in a child element.
        val label = row.selectFirst(Ao3Selectors.TAG_SEARCH_LABEL)?.ownText().orEmpty()
        val type = TYPE.find(label)?.groupValues?.get(1)?.let(TagType::fromLabel) ?: TagType.Unknown
        val workCount = COUNT.find(label)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()

        return TagRef(name = name, type = type, href = href, workCount = workCount)
    }
}
