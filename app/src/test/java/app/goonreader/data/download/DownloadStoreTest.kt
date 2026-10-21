package app.goonreader.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DownloadStoreTest {

    private fun tempDir() = Files.createTempDirectory("downloads").toFile()

    @Test
    fun `write, read and remove round trip`() {
        val store = DownloadStore(tempDir())

        assertFalse(store.contains("1", "2"))
        store.write("1", "2", "<p>chapter</p>")

        assertTrue(store.contains("1", "2"))
        assertEquals("<p>chapter</p>", store.read("1", "2"))

        store.remove("1", "2")
        assertFalse(store.contains("1", "2"))
        assertNull(store.read("1", "2"))
    }

    @Test
    fun `a chapter written twice keeps the last copy`() {
        val store = DownloadStore(tempDir())

        store.write("1", "2", "first")
        store.write("1", "2", "second")

        assertEquals("second", store.read("1", "2"))
    }

    /** Ids come off AO3 pages; a crafted one must not read or write outside the download dir. */
    @Test
    fun `path-like ids stay inside the directory`() {
        val dir = tempDir()
        val store = DownloadStore(dir)

        store.write("../escape", "x/../y", "text")

        assertEquals(listOf("wescapecxy.html.gz"), dir.listFiles()!!.map { it.name })
        assertEquals("text", store.read("../escape", "x/../y"))
    }
}
