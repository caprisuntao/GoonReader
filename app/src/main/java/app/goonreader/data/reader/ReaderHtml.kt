package app.goonreader.data.reader

import app.goonreader.data.prefs.ReaderSettings
import app.goonreader.data.prefs.ReaderTheme
import app.goonreader.domain.model.ChapterContent

/** Colours for one reader theme. Hex strings because their only consumer is CSS. */
data class ReaderPalette(
    val background: String,
    val text: String,
    val muted: String,
    val accent: String,
    val rule: String,
    val isDark: Boolean,
)

fun ReaderTheme.palette(): ReaderPalette = when (this) {
    ReaderTheme.Day -> ReaderPalette("#FAF6F0", "#2B2622", "#6B5F52", "#A9552B", "#D9CFC2", false)
    ReaderTheme.Night -> ReaderPalette("#17120E", "#D8D0C4", "#9C9184", "#E3A374", "#3A3128", true)
    ReaderTheme.Sepia -> ReaderPalette("#F1E6CF", "#4A3A28", "#7A6749", "#8A5A2B", "#DCCBA9", false)
    ReaderTheme.Amoled -> ReaderPalette("#000000", "#CFC8BD", "#8E857A", "#E3A374", "#2A2A2A", true)
    // Custom colours come from the settings; this fallback is never rendered.
    ReaderTheme.Custom -> ReaderPalette("#1B1612", "#E6DCCF", "#9C9184", "#E3A374", "#3A3128", true)
}

/** PRD 10.4: a custom background and text colour, with the rest derived to keep it readable. */
fun ReaderSettings.palette(): ReaderPalette =
    if (theme == ReaderTheme.Custom) customPalette(customBackground, customText) else theme.palette()

fun customPalette(background: Int, text: Int): ReaderPalette {
    val dark = isDark(background)
    return ReaderPalette(
        background = hex(background),
        text = hex(text),
        muted = hex(blend(text, background, 0.45f)),
        accent = if (dark) "#E3A374" else "#A9552B",
        rule = hex(blend(text, background, 0.75f)),
        isDark = dark,
    )
}

private fun hex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

private fun blend(from: Int, to: Int, amount: Float): Int {
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * amount).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** Rec. 601 luminance is plenty to decide light or dark here. */
private fun isDark(color: Int): Boolean {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    return (0.299f * r + 0.587f * g + 0.114f * b) < 128f
}

/**
 * Everything the HTML depends on, in CSS pixels already. The screen converts sp/dp to px
 * (CSS px == dp, and the system font scale multiplies sp), so this stays a pure value and the
 * renderer is testable without Android.
 */
data class ReaderAppearance(
    val palette: ReaderPalette,
    val fontPx: Float,
    val lineSpacing: Float,
    val paragraphSpacingEm: Float,
    val sideMarginPx: Int,
    val verticalMarginPx: Int,
    val justify: Boolean,
    val showSummary: Boolean,
    val showNotes: Boolean,
    /** PRD 10.1: horizontal pages via CSS multi-column, one column per viewport width. */
    val paged: Boolean = false,
    /** An `@font-face` rule for an imported font, empty for the system font. */
    val fontFaceCss: String = "",
)

/**
 * Builds the document the WebView renders.
 *
 * Appearance changes re-render and reload the document rather than restyling it in place: the
 * reader runs no page script of its own (the WebView's script setting is on only so the
 * highlight can be moved, see [WebViewReaderEngine]), so there is no DOM to poke at. Reloading
 * a string is cheap and the screen restores position.
 */
object ReaderHtml {

    fun render(content: ChapterContent, appearance: ReaderAppearance): String = buildString {
        val body = ReaderText.annotate(content.bodyHtml)
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<style>")
        append(css(appearance))
        append("</style></head><body>")
        append(if (appearance.paged) "<article id=\"pages\">" else "<article>")
        content.title?.takeIf { it.isNotBlank() }?.let {
            append("<h1 class=\"chapter-title\">").append(escape(it)).append("</h1>")
        }
        if (appearance.showSummary) {
            content.summaryHtml?.takeIf { it.isNotBlank() }?.let {
                append("<div class=\"summary\">").append(it).append("</div>")
            }
        }
        if (appearance.showNotes) {
            content.startNotesHtml?.takeIf { it.isNotBlank() }?.let {
                append("<div class=\"notes start\">").append(it).append("</div>")
            }
        }
        append("<div class=\"chapter-body\">").append(body.html).append("</div>")
        if (appearance.showNotes) {
            content.endNotesHtml?.takeIf { it.isNotBlank() }?.let {
                append("<div class=\"notes end\">").append(it).append("</div>")
            }
        }
        append("</article></body></html>")
    }

    private fun css(a: ReaderAppearance): String {
        val p = a.palette
        val gap = a.sideMarginPx * 2
        return buildString {
            append(a.fontFaceCss)
            append(":root{color-scheme:").append(if (p.isDark) "dark" else "light").append("}")
            if (a.paged) {
                // Height is pinned to the viewport so columns break at the page edge; horizontal
                // overflow is what the engine scrolls through, one viewport per page.
                append("html,body{margin:0;padding:0;height:100%;background:").append(p.background)
                append(";color:").append(p.text).append("}")
                append("#pages{height:100%;box-sizing:border-box;padding:")
                append(a.verticalMarginPx).append("px ").append(a.sideMarginPx).append("px;")
                append("column-width:calc(100vw - ").append(gap).append("px);")
                append("column-gap:").append(gap).append("px;column-fill:auto}")
            } else {
                append("html,body{margin:0;padding:0;background:").append(p.background)
                append(";color:").append(p.text).append("}")
                append("body{")
                // Bottom padding leaves room to scroll the last line above the chrome.
                append("padding:").append(a.verticalMarginPx).append("px ")
                append(a.sideMarginPx).append("px 48px;")
                append("}")
            }
            append("body{font-family:")
            append(if (a.fontFaceCss.isNotBlank()) "'GoonReaderCustom'," else "")
            append("system-ui,-apple-system,\"Segoe UI\",Roboto,sans-serif;")
            append("font-size:").append(a.fontPx).append("px;")
            append("line-height:").append(a.lineSpacing).append(";")
            append("text-align:").append(if (a.justify) "justify" else "start").append(";")
            append("overflow-wrap:break-word;word-wrap:break-word;-webkit-text-size-adjust:100%;")
            append("margin:0}")
            append("p{margin:0 0 ").append(a.paragraphSpacingEm).append("em}")
            append("h1.chapter-title{font-size:1.18em;line-height:1.25;font-weight:600;margin:0 0 .9em;color:")
            append(p.text).append("}")
            append("hr{border:0;border-top:1px solid ").append(p.rule).append(";margin:1.2em 0}")
            append("a{color:").append(p.accent).append(";text-decoration:none}")
            append("em,i{font-style:italic}strong,b{font-weight:600}")
            append("blockquote{margin:0 0 ").append(a.paragraphSpacingEm).append("em;padding-left:.9em;")
            append("border-left:3px solid ").append(p.rule).append(";color:").append(p.muted).append("}")
            append("img{max-width:100%;height:auto}")
            append("pre,code{font-family:monospace;font-size:.92em;white-space:pre-wrap}")
            append(".summary,.notes{font-size:.94em;color:").append(p.muted)
            append(";border-left:3px solid ").append(p.rule).append(";padding-left:.8em;margin:0 0 1.2em}")
            append(".notes.end{margin-top:1.4em}")
            append(".chapter-body p:last-child{margin-bottom:0}")
            // TTS: the paragraph being read aloud, painted with the theme accent at low alpha.
            // The transition lets the highlight move between paragraphs instead of blinking.
            append("[data-p]{transition:background-color 240ms ease,box-shadow 240ms ease}")
            append("@media (prefers-reduced-motion: reduce){[data-p]{transition:none}}")
            append(".tts-active{background:").append(p.accent).append("33;border-radius:6px;")
            append("box-shadow:0 0 0 3px ").append(p.accent).append("33}")
        }
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }
}
