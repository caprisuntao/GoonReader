package app.goonreader.data.reader

import org.jsoup.Jsoup

/**
 * Chapter body split into the blocks text-to-speech reads, with the same order and numbering
 * baked into the rendered HTML as `data-p="i"`.
 *
 * One function produces both sides so they cannot drift: TTS speaks `paragraphs[i]` while the
 * reader highlights the element carrying `data-p="i"`.
 */
data class ReaderBody(
    val html: String,
    val paragraphs: List<String>,
)

object ReaderText {

    fun annotate(bodyHtml: String): ReaderBody {
        val body = Jsoup.parseBodyFragment(bodyHtml).body()
        val blocks = body.children().filter { it.text().isNotBlank() }

        if (blocks.isEmpty()) {
            // Bare text or a single <br>-separated run: still readable aloud, as one block.
            val text = body.text().trim()
            if (text.isEmpty()) return ReaderBody("", emptyList())
            body.empty()
            body.appendElement("p").attr(DATA_P, "0").text(text)
            return ReaderBody(body.html(), listOf(text))
        }

        blocks.forEachIndexed { index, block -> block.attr(DATA_P, index.toString()) }
        return ReaderBody(body.html(), blocks.map { it.text() })
    }

    const val DATA_P = "data-p"
}
