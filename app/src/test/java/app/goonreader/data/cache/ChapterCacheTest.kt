package app.goonreader.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.Random

class ChapterCacheTest {

    private fun directory() = Files.createTempDirectory("chapter-cache").toFile()

    @Test
    fun `a written chapter reads back byte for byte`() {
        val cache = ChapterCache(directory())
        val html = "<!DOCTYPE html><html><body><p>hello &amp; goodbye</p></body></html>"
        cache.write("4701869", "10736366", html)
        assertEquals(html, cache.read("4701869", "10736366"))
    }

    @Test
    fun `chapters are scoped by work and chapter`() {
        val cache = ChapterCache(directory())
        cache.write("1", "10", "ten")
        assertNull(cache.read("1", "11"))
        assertNull(cache.read("2", "10"))
    }

    @Test
    fun `removing a chapter forgets it`() {
        val cache = ChapterCache(directory())
        cache.write("1", "10", "ten")
        assertTrue(cache.contains("1", "10"))
        cache.remove("1", "10")
        assertFalse(cache.contains("1", "10"))
    }

    /**
     * The cache is size-capped (PRD 4): an entry that alone exceeds the cap cannot stay, otherwise
     * the cap would be a suggestion.
     */
    @Test
    fun `an entry larger than the cap is evicted`() {
        val cache = ChapterCache(directory(), maxBytes = 3_000)
        cache.write("1", "1", incompressibleText(16_000))
        assertFalse(cache.contains("1", "1"))
    }

    private fun incompressibleText(length: Int): String {
        val random = Random(42)
        return buildString {
            repeat(length) { append("0123456789abcdef"[random.nextInt(16)]) }
        }
    }
}
