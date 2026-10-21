package app.goonreader.data.repo

import app.goonreader.data.network.Ao3Client
import app.goonreader.data.network.Ao3Page
import app.goonreader.data.network.Ao3Urls
import app.goonreader.data.parser.SearchParser
import app.goonreader.data.parser.TagSearchParser
import app.goonreader.domain.model.SearchFilters
import app.goonreader.domain.model.SortColumn
import app.goonreader.domain.model.SortDirection
import app.goonreader.domain.model.TagRef
import app.goonreader.domain.model.TagType
import app.goonreader.domain.model.Work
import javax.inject.Inject
import javax.inject.Singleton

/** One page of results, plus whether AO3 has another page after it. */
data class SearchPage(val works: List<Work>, val hasNext: Boolean)

/**
 * Browse: search and tag pages. Read-only — a preview must never write (PRD 4), so this
 * deliberately has no database at all.
 */
@Singleton
class SearchRepository @Inject constructor(private val client: Ao3Client) {

    suspend fun search(filters: SearchFilters, page: Int): RepoResult<SearchPage> =
        searchPage(Ao3Urls.search(filters, page))

    suspend fun tagWorks(
        tagHref: String,
        sortColumn: SortColumn,
        sortDirection: SortDirection,
        page: Int,
    ): RepoResult<SearchPage> = searchPage(Ao3Urls.tagWorks(tagHref, sortColumn, sortDirection, page))

    /** Tags matching a typed name, for the tag picker. */
    suspend fun searchTags(name: String, type: TagType?): RepoResult<List<TagRef>> {
        val page = client.get(Ao3Urls.tagSearch(name, type))
        if (page !is Ao3Page.Success) return RepoResult.Failed(page.toFailureReason())
        return RepoResult.Ok(TagSearchParser.parse(page.html))
    }

    private suspend fun searchPage(url: String): RepoResult<SearchPage> {
        val page = client.get(url)
        if (page !is Ao3Page.Success) return RepoResult.Failed(page.toFailureReason())
        val works = SearchParser.parse(page.html)
        // Search pages mark "next" in their pagination, tag pages only number the links. A full
        // page is the one signal both layouts share; a page that comes back empty ends the list.
        return RepoResult.Ok(SearchPage(works, hasNext = works.size >= RESULTS_PER_PAGE))
    }

    companion object {
        /** AO3 returns 20 works per results page. */
        const val RESULTS_PER_PAGE = 20
    }
}
