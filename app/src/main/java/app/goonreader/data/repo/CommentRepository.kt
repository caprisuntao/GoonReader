package app.goonreader.data.repo

import app.goonreader.data.network.Ao3Client
import app.goonreader.data.network.Ao3Page
import app.goonreader.data.network.Ao3Urls
import app.goonreader.data.parser.CommentParser
import app.goonreader.domain.model.CommentPage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chapter comments (PRD 9.10/7.3): one page per request through the shared client, so the rate
 * limiter applies and nothing is prefetched. Comments are never stored; opening the sheet again
 * re-fetches, because they always need the network anyway.
 */
@Singleton
class CommentRepository @Inject constructor(private val client: Ao3Client) {

    suspend fun load(workId: String, chapterId: String, page: Int): RepoResult<CommentPage> {
        val result = client.get(Ao3Urls.comments(workId, chapterId, page))
        if (result !is Ao3Page.Success) return RepoResult.Failed(result.toFailureReason())
        return RepoResult.Ok(CommentParser.parse(result.html))
    }
}
