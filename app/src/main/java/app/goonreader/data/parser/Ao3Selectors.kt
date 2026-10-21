package app.goonreader.data.parser

/**
 * Every AO3 CSS selector the app depends on, in one place.
 *
 * AO3 has no API; these strings are the contract. When AO3 changes its markup the fix
 * belongs here (and in the fixture that proves it), not scattered through parsers.
 * Verified against the saved pages in `app/src/test/resources/fixtures`.
 */
object Ao3Selectors {

    // --- Search / tag results -------------------------------------------------
    /** One result row. Bookmark blurbs share the shape, so both are accepted. */
    const val SEARCH_RESULT = "li.work.blurb, li.bookmark.blurb"
    const val BLURB_TITLE_LINK = "h4.heading a[href*='/works/']"
    const val BLURB_AUTHOR = "h4.heading a[rel=author]"
    const val BLURB_FANDOM = "h5.fandoms a.tag"
    const val BLURB_TAG_LIST = "ul.tags"
    const val BLURB_SUMMARY = "blockquote.userstuff.summary"
    const val BLURB_DATE = "p.datetime"

    /** Rating / warning / category / WIP badges. The label is in the `title` attribute. */
    const val BLURB_RATING = "ul.required-tags span.rating"
    const val BLURB_WARNING = "ul.required-tags span.warnings"
    const val BLURB_CATEGORY = "ul.required-tags span.category"
    const val BLURB_WIP = "ul.required-tags span.iswip"

    // --- Work page ------------------------------------------------------------
    const val WORK_TITLE = "h2.title.heading"
    const val WORK_AUTHOR = "h3.byline.heading a[rel=author]"
    const val WORK_META = "dl.work.meta.group"
    const val WORK_PREFACE = "#workskin > div.preface.group"
    const val WORK_SUMMARY = "div.summary.module blockquote.userstuff"
    const val WORK_NOTES = "div.notes.module"

    /** Stats block; the same shape appears in blurbs and on work pages. */
    const val STATS = "dl.stats"

    /** Meta rows inside [WORK_META]; each contains a `ul.commas` of `a.tag` links. */
    const val META_RATING = "dd.rating a.tag"
    const val META_WARNING = "dd.warning a.tag"
    const val META_CATEGORY = "dd.category a.tag"
    const val META_FANDOM = "dd.fandom a.tag"
    const val META_RELATIONSHIP = "dd.relationship a.tag"
    const val META_CHARACTER = "dd.character a.tag"
    const val META_FREEFORM = "dd.freeform a.tag"

    // --- Chapter index (`/works/<id>/navigate`) --------------------------------
    const val CHAPTER_INDEX_ITEM = "ol.chapter.index.group > li"
    const val CHAPTER_INDEX_LINK = "a[href*='/chapters/']"
    const val CHAPTER_INDEX_DATE = "span.datetime"

    // --- Chapter content ------------------------------------------------------
    /** Wrapper that exists on both work pages and dedicated chapter pages. */
    const val CHAPTER_CONTAINER = "#chapters"
    /** Body text. `.userstuff.module` on multi-chapter pages, plain `.userstuff` on one-shots. */
    const val CHAPTER_BODY = ".userstuff"
    const val CHAPTER_TITLE = "div.chapter h3.title"
    const val CHAPTER_SUMMARY = "div.summary.module blockquote.userstuff"
    const val CHAPTER_NOTES = "div.notes.module"
    /** Screen-reader-only labels ("Chapter Text", "Work Text") inside the body. */
    const val LANDMARK_HEADING = "h3.landmark.heading"

    // --- Tag search (`/tags/search`) -------------------------------------------
    /** One row of the tag search result list: `Freeform: <a class="tag" href="/tags/...">name</a> (12)`. */
    const val TAG_SEARCH_LIST = "ol.tag.index.group"
    const val TAG_SEARCH_RESULT = "$TAG_SEARCH_LIST > li"
    const val TAG_SEARCH_LINK = "a.tag"
    const val TAG_SEARCH_LABEL = "span"

    // --- Error / interstitial pages ------------------------------------------
    const val ADULT_WARNING = "p.caution.notice"
    const val ERROR_PAGE = "#main.system.errors"

    // --- Chapter comments (PRD 9.10) ------------------------------------------
    /** Present on every chapter page; hidden and empty unless comments were requested. */
    const val COMMENTS_CONTAINER = "#comments_placeholder"
    const val COMMENT = "li.comment.group"
    const val COMMENT_BYLINE = "h4.heading.byline"
    const val COMMENT_AUTHOR_LINK = "a[href*='/users/']"
    const val COMMENT_BODY = "blockquote.userstuff"
    const val COMMENT_POSTED = "span.posted"
    const val COMMENT_DAY = "span.date"
    const val COMMENT_MONTH = "abbr.month"
    const val COMMENT_YEAR = "span.year"
    /** Comments pagination, scoped to the container so chapter pagination can never match. */
    const val COMMENT_PAGINATION = "#comments_placeholder ol.pagination"
    /** "Comments (741)" / "Hide Comments (741)"; the count is printed on every chapter page. */
    const val COMMENT_COUNT_LINK = "#show_comments_link a"

    // --- Stats rows (shared by blurbs and work pages) -------------------------
    const val STAT_LANGUAGE = "dd.language"
    const val STAT_WORDS = "dd.words"
    const val STAT_CHAPTERS = "dd.chapters"
    const val STAT_COMMENTS = "dd.comments"
    const val STAT_KUDOS = "dd.kudos"
    const val STAT_BOOKMARKS = "dd.bookmarks"
    const val STAT_HITS = "dd.hits"
    const val STAT_PUBLISHED = "dd.published"
    const val STAT_STATUS = "dd.status"
    const val STAT_STATUS_LABEL = "dt.status"

    /** `li` class names carrying tag groups inside [BLURB_TAG_LIST]. */
    const val TAG_GROUP_RELATIONSHIP = "relationships"
    const val TAG_GROUP_CHARACTER = "characters"
    const val TAG_GROUP_FREEFORM = "freeforms"
    const val TAG_GROUP_WARNING = "warnings"

    private val WORK_ID = Regex("/works/(\\d+)")
    private val CHAPTER_ID = Regex("/chapters/(\\d+)")

    fun workIdFromHref(href: String?): String? = href?.let { WORK_ID.find(it)?.groupValues?.get(1) }

    fun chapterIdFromHref(href: String?): String? = href?.let { CHAPTER_ID.find(it)?.groupValues?.get(1) }
}
