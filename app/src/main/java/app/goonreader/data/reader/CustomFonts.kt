package app.goonreader.data.reader

import android.content.Context
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imported reader fonts (PRD 10.4).
 *
 * The WebView cannot read a file:// font (file access is off, and the page's base URL is AO3),
 * so the font travels into the document as a `data:` URI. The base64 is built once per file and
 * cached, because every appearance change re-renders the chapter.
 *
 * `shortcut: the whole font is embedded in every render; cap it and move to a served asset if
 * start-up cost ever matters.`
 */
@Singleton
class CustomFonts @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir = File(context.filesDir, "fonts").apply { mkdirs() }
    private val cache = HashMap<String, String>()

    /** The `@font-face` rule for [path], or "" when there is no usable file. */
    @Synchronized
    fun css(path: String): String {
        if (path.isBlank()) return ""
        cache[path]?.let { return it }
        val file = File(path)
        if (!file.exists() || file.length() > MAX_BYTES) return ""
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return ""
        val mime = if (file.name.endsWith(".otf", ignoreCase = true)) "font/otf" else "font/ttf"
        val css = "@font-face{font-family:'GoonReaderCustom';src:url(data:$mime;base64," +
            Base64.encodeToString(bytes, Base64.NO_WRAP) + ")}"
        cache[path] = css
        return css
    }

    /** Copies the picked file into app storage; the result is the path to persist. */
    suspend fun import(uri: Uri, previous: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(dir, "reader-${System.currentTimeMillis()}.ttf")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("Could not open that file.")
                var total = 0L
                input.use { stream ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = stream.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_BYTES) error("Font is too large (max 1.5 MB).")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (total == 0L) error("That file is empty.")
            } catch (failure: Throwable) {
                target.delete()
                throw failure
            }
            remove(previous)
            target.absolutePath
        }
    }

    @Synchronized
    fun remove(path: String) {
        if (path.isBlank()) return
        cache.remove(path)
        File(path).delete()
    }

    private companion object {
        /** A big enough font embeds as ~2 MB of base64 in every chapter render. */
        const val MAX_BYTES = 1_500_000L
    }
}
