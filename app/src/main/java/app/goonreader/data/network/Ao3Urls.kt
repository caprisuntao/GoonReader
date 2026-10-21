package app.goonreader.data.network

import app.goonreader.domain.model.SearchFilters
import app.goonreader.domain.model.SortColumn
import app.goonreader.domain.model.SortDirection
import app.goonreader.domain.model.TagType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * URL construction only. Nothing here is allowed to hand-encode a tag name: tag pages are
 * always followed from an `href` that came off an AO3 page (see [resolve]).
 *
 * The `work_search[...]` parameter names below are AO3's own, copied from the live search
 * form (fixture `13-search-form.html`) rather than invented.
 */
object Ao3Urls {

    private val BASE = "https://archiveofourown.org".toHttpUrl()

    /**
     * `view_adult=true` skips the adult-content interstitial and records consent as a cookie.
     * Sending it on every work/chapter request costs nothing and means mature works load on the
     * first try instead of needing a second round trip.
     */
    fun work(workId: String): String =
        BASE.newBuilder()
            .addPathSegments("works/$workId")
            .addQueryParameter("view_adult", "true")
            .build()
            .toString()

    fun chapter(workId: String, chapterId: String): String =
        BASE.newBuilder()
            .addPathSegments("works/$workId/chapters/$chapterId")
            .addQueryParameter("view_adult", "true")
            .build()
            .toString()

    fun navigate(workId: String): String =
        BASE.newBuilder().addPathSegments("works/$workId/navigate").build().toString()

    /**
     * A chapter's comments. AO3 normally loads them over an AJAX endpoint, but asking the same
     * chapter page with `show_comments=true` returns the full document with comments embedded
     * and proper pagination (verified against fixture `08-chapter-comments.html`).
     */
    fun comments(workId: String, chapterId: String, page: Int = 1): String {
        val base = if (chapterId == workId) work(workId) else chapter(workId, chapterId)
        var url = base.toHttpUrl().newBuilder().addQueryParameter("show_comments", "true")
        if (page > 1) url = url.addQueryParameter("page", page.toString())
        return url.build().toString()
    }

    /** Work search. One page of 20 results. */
    fun search(filters: SearchFilters, page: Int = 1): String {
        var url = BASE.newBuilder().addPathSegments("works/search")
        fun param(name: String, value: String?) {
            if (!value.isNullOrBlank()) url = url.addQueryParameter("work_search[$name]", value)
        }

        param("query", filters.query)
        param("title", filters.title)
        param("creators", filters.creators)
        param("fandom_names", filters.fandomNames)
        param("relationship_names", filters.relationshipNames)
        param("character_names", filters.characterNames)
        param("freeform_names", filters.freeformNames)
        param("word_count", filters.wordCountParam())

        // AO3's search form submits rating as a single value; the results page itself echoes it
        // back as an array, and both are accepted. Sent as an array so every *_ids param matches.
        filters.rating?.let { url = url.addQueryParameter("work_search[rating_ids][]", it.id) }
        filters.warnings.forEach { url = url.addQueryParameter("work_search[archive_warning_ids][]", it.id) }
        filters.categories.forEach { url = url.addQueryParameter("work_search[category_ids][]", it.id) }

        filters.completion.value?.let { url = url.addQueryParameter("work_search[complete]", it) }
        filters.crossovers.value?.let { url = url.addQueryParameter("work_search[crossover]", it) }
        if (filters.singleChapterOnly) url = url.addQueryParameter("work_search[single_chapter]", "1")
        filters.languageId?.let { url = url.addQueryParameter("work_search[language_id]", it) }
        filters.updatedWithin.value?.let { url = url.addQueryParameter("work_search[revised_at]", it) }

        url = url.withSort(filters.sortColumn, filters.sortDirection)
        return url.withPage(page).build().toString()
    }

    /**
     * One page of works carrying a tag. [tagHref] must come from a parsed page; the `/works`
     * suffix and the sort parameters are then added around it.
     */
    fun tagWorks(
        tagHref: String,
        sortColumn: SortColumn = SortColumn.BestMatch,
        sortDirection: SortDirection = SortDirection.Descending,
        page: Int = 1,
    ): String {
        var url = tagWorksUrl(tagHref).newBuilder()
        url = url.withSort(sortColumn, sortDirection)
        return url.withPage(page).build().toString()
    }

    /** Tag search: "type a tag, pick from results". */
    fun tagSearch(name: String, type: TagType? = null, page: Int = 1): String {
        var url = BASE.newBuilder().addPathSegments("tags/search")
        if (name.isNotBlank()) url = url.addQueryParameter("tag_search[name]", name)
        type?.ao3Value?.let { url = url.addQueryParameter("tag_search[type]", it) }
        return url.withPage(page).build().toString()
    }

    /** Turn an `href` copied from a parsed page into an absolute URL. */
    fun resolve(href: String): String =
        BASE.resolve(href)?.toString() ?: href

    /** Absolute tag page URL ending in `/works`, built from a parsed tag `href`. */
    fun tagWorksUrl(tagHref: String): HttpUrl {
        val path = resolve(tagHref).trimEnd('/').let { if (it.endsWith("/works")) it else "$it/works" }
        return path.toHttpUrlOrNull() ?: BASE.resolve(path)!!
    }

    private fun HttpUrl.Builder.withSort(column: SortColumn, direction: SortDirection): HttpUrl.Builder {
        // Null means "let AO3 decide": best match for a search, date updated for a tag page.
        if (column.value != null) {
            addQueryParameter("work_search[sort_column]", column.value)
            addQueryParameter("work_search[sort_direction]", direction.value)
        }
        return this
    }

    /** AO3 prints page 1 as the bare URL, which keeps search URLs stable for caching. */
    private fun HttpUrl.Builder.withPage(page: Int): HttpUrl.Builder = apply {
        if (page > 1) addQueryParameter("page", page.toString())
    }
}
