package app.goonreader.data.repo

import app.goonreader.Fixture
import app.goonreader.data.cache.ChapterCache
import app.goonreader.data.cache.ChapterCodec
import app.goonreader.data.db.ChapterDao
import app.goonreader.data.db.ChapterEntity
import app.goonreader.data.db.UpdateRow
import app.goonreader.data.download.DownloadStore
import app.goonreader.data.network.Ao3Client
import app.goonreader.data.network.Ao3Raw
import app.goonreader.data.network.Ao3Transport
import app.goonreader.data.network.RateLimiter
import app.goonreader.data.parser.ChapterParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ChapterRepositoryTest {

    private val workId = "4701869"
    private val chapterId = "10736366"
    private val chapterPage = Fixture.load("05-work-chapter1.html")

    @Test
    fun `a fetched chapter is parsed, returned and cached`() = runTest {
        val cache = cache()
        val repository = repository(cache = cache)

        val result = repository.getChapter(workId, chapterId)

        assertTrue(result is RepoResult.Ok)
        assertTrue((result as RepoResult.Ok).value.bodyHtml.contains("dreaming of it since the defeat of Voldemort"))
        assertTrue(cache.contains(workId, chapterId))
    }

    @Test
    fun `a cached chapter does not touch the network`() = runTest {
        val cache = cache()
        var requests = 0
        val repository = repository(cache = cache, onRequest = { requests++ })

        repository.getChapter(workId, chapterId)
        repository.getChapter(workId, chapterId)

        assertEquals(1, requests)
    }

    @Test
    fun `a downloaded chapter is served with no network and beats a stale cache`() = runTest {
        val store = downloadStore()
        val content = checkNotNull(ChapterParser.parse(chapterPage, workId, chapterId))
        store.write(workId, chapterId, ChapterCodec.encode(content))
        var requests = 0
        val repository = repository(downloads = store, onRequest = { requests++ })

        val result = repository.getChapter(workId, chapterId) as RepoResult.Ok

        assertTrue(result.value.bodyHtml.contains("dreaming of it since the defeat of Voldemort"))
        assertEquals(0, requests)
    }

    @Test
    fun `a challenge is reported as a challenge`() = runTest {
        val repository = repository(
            response = { Ao3Raw(403, "<script src=\"/cdn-cgi/challenge-platform/x\"></script>") },
        )
        assertEquals(FailureReason.ChallengeRequired, (repository.getChapter(workId, chapterId) as RepoResult.Failed).reason)
    }

    @Test
    fun `a page without chapter text is a parse error`() = runTest {
        val repository = repository(response = { Ao3Raw(200, "<html><body><p>nothing here</p></body></html>") })
        assertEquals(FailureReason.ParseError, (repository.getChapter(workId, chapterId) as RepoResult.Failed).reason)
    }

    @Test
    fun `prefetch fills the cache once`() = runTest {
        val cache = cache()
        var requests = 0
        val repository = repository(cache = cache, onRequest = { requests++ })

        repository.prefetch(workId, chapterId)
        assertTrue(cache.contains(workId, chapterId))
        repository.prefetch(workId, chapterId)

        assertEquals(1, requests)
    }

    @Test
    fun `reaching the threshold marks the chapter read`() = runTest {
        val dao = FakeChapterDao()
        val repository = repository(dao = dao)

        repository.saveProgress(chapterId, 0.97f, markReadThreshold = 0.95f)

        assertEquals(0.97f, dao.progress[chapterId])
        assertTrue(dao.read[chapterId] == true)
    }

    @Test
    fun `below the threshold the chapter stays unread`() = runTest {
        val dao = FakeChapterDao()
        val repository = repository(dao = dao)

        repository.saveProgress(chapterId, 0.4f, markReadThreshold = 0.95f)

        assertEquals(0.4f, dao.progress[chapterId])
        assertFalse(dao.read[chapterId] == true)
    }

    private fun cache() = ChapterCache(Files.createTempDirectory("chapters").toFile())

    private fun downloadStore() = DownloadStore(Files.createTempDirectory("downloads").toFile())

    private fun repository(
        cache: ChapterCache = cache(),
        downloads: DownloadStore = downloadStore(),
        dao: ChapterDao = FakeChapterDao(),
        onRequest: () -> Unit = {},
        response: () -> Ao3Raw = { Ao3Raw(200, chapterPage) },
    ) = ChapterRepository(
        client = Ao3Client(
            RateLimiter(interactiveMillis = 0, backgroundMillis = 0),
            Ao3Transport { onRequest(); response() },
        ),
        cache = cache,
        downloads = downloads,
        chapterDao = dao,
    )

    private class FakeChapterDao : ChapterDao {
        val read = mutableMapOf<String, Boolean>()
        val progress = mutableMapOf<String, Float>()
        val downloaded = mutableMapOf<String, Boolean>()

        override suspend fun getForWork(workId: String): List<ChapterEntity> = error("unused")
        override suspend fun getById(chapterId: String): ChapterEntity? = error("unused")
        override suspend fun upsert(chapter: ChapterEntity) = error("unused")
        override suspend fun upsertAll(chapters: List<ChapterEntity>) = error("unused")
        override suspend fun setRead(chapterId: String, read: Boolean) {
            this.read[chapterId] = read
        }

        override suspend fun setProgress(chapterId: String, progress: Float) {
            this.progress[chapterId] = progress
        }

        override suspend fun setDownloaded(chapterId: String, downloaded: Boolean) {
            this.downloaded[chapterId] = downloaded
        }

        override suspend fun clearDownloadFlags() {
            downloaded.clear()
        }

        override fun observeUpdates(limit: Int): Flow<List<UpdateRow>> = error("unused")

        override suspend fun forLibrary(): List<ChapterEntity> = error("unused")
    }
}
