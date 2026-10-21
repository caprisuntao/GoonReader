package app.goonreader.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared parsing helpers. AO3 renders numbers with thousands separators and dates in two
 * shapes depending on the page, so every parser needs the same lenient conversion.
 */
internal object Ao3Parse {

    private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val CHAPTERS_RATIO = Regex("^\\s*(\\d+)\\s*/\\s*(\\d+|\\?)\\s*$")
    private val PARENTHESISED_COUNT = Regex("\\((\\d[\\d,]*)\\)")

    /** "151,873" -> 151873. Null for missing or unparseable text. */
    fun count(text: String?): Int? =
        text?.replace(",", "")?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

    /** Same, for values that exceed Int (hit counts). */
    fun longCount(text: String?): Long? =
        text?.replace(",", "")?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

    /**
     * "50/50" -> 50 to 50; "3/?" -> 3 to null. Returns nulls when the field is absent.
     * AO3 uses this shape for both chapters and (unused here) comment counts.
     */
    fun chapters(text: String?): Pair<Int?, Int?> {
        val match = CHAPTERS_RATIO.find(text.orEmpty()) ?: return null to null
        val published = match.groupValues[1].toIntOrNull()
        val total = match.groupValues[2].let { if (it == "?") null else it.toIntOrNull() }
        return published to total
    }

    /** "2015-09-01" (chapter index, work stats). */
    fun isoDate(text: String?): LocalDate? =
        text?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it, ISO_DATE) }.getOrNull() }

    /** "02 Sep 2015" (search blurbs, comment timestamps). */
    fun shortDate(text: String?): LocalDate? =
        text?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it, SHORT_DATE) }.getOrNull() }

    /** "Comments (741)" -> 741. AO3 prints totals in parentheses in several places. */
    fun parenthesisedCount(text: String?): Int? =
        text?.let { PARENTHESISED_COUNT.find(it)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() }

    /** Dates on the chapter index are wrapped in parentheses: "(2015-09-01)". */
    fun parenthesisedDate(text: String?): LocalDate? =
        isoDate(text?.trim()?.removeSurrounding("(", ")")?.trim())

    fun text(element: Element?): String? = element?.text()?.trim()?.takeIf { it.isNotEmpty() }

    /** Tag labels for one group (`li.relationships`, `li.characters`, ...). */
    fun blurbTags(scope: Element?, groupClass: String): List<String> =
        scope?.select("${Ao3Selectors.BLURB_TAG_LIST} li.$groupClass a.tag")
            ?.map { it.text().trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /** Tag labels for an arbitrary selector, used by the work-page meta rows. */
    fun tags(scope: Element?, selector: String): List<String> =
        scope?.select(selector)?.map { it.text().trim() }?.filter { it.isNotEmpty() }.orEmpty()
}

/**
 * Strips the parts of AO3's user-authored HTML that must never reach a WebView.
 *
 * AO3 already filters work HTML on input, so this is a second boundary rather than the
 * only one: it removes scriptable elements and event-handler attributes, keeps everything
 * that carries meaning (tags, classes, inline style, links, images).
 */
internal object Ao3Html {

    private const val DROP_ALL = "script, style, iframe, frame, object, embed, form, input, button, link, meta, base"

    fun clean(fragment: String): String {
        val body = Jsoup.parseBodyFragment(fragment).body()
        body.select(DROP_ALL).remove()
        body.allElements.forEach { element ->
            element.attributes().map { it.key }.forEach { key ->
                val value = element.attr(key)
                val isHandler = key.startsWith("on", ignoreCase = true)
                val isUrlAttr = key.equals("href", ignoreCase = true) || key.equals("src", ignoreCase = true)
                val isScriptUrl = isUrlAttr && value.trimStart().startsWith("javascript:", ignoreCase = true)
                if (isHandler || isScriptUrl) element.removeAttr(key)
            }
        }
        return body.html()
    }

    /** Inner HTML minus screen-reader-only landmark headings. */
    fun contentWithoutLandmarks(element: Element): String {
        val copy = element.clone()
        copy.select(Ao3Selectors.LANDMARK_HEADING).remove()
        return clean(copy.html())
    }
}
