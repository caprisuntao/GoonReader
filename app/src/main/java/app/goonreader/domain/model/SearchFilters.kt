package app.goonreader.domain.model

/**
 * One AO3 language. The id is the value of the search form's `work_search[language_id]`
 * option (ISO 639-1 where AO3 has one), read off the live form.
 */
data class Language(val id: String, val label: String)

/**
 * The subset of AO3's ~170 languages that is worth a dropdown on a phone. Labels are English
 * so the list stays consistent; AO3's own names are native, which is a worse fit for a filter
 * menu. Ids were read off the live form, not guessed.
 */
val AO3_LANGUAGES: List<Language> = listOf(
    Language("en", "English"),
    Language("es", "Spanish"),
    Language("fr", "French"),
    Language("de", "German"),
    Language("ru", "Russian"),
    Language("zh", "Chinese"),
    Language("ja", "Japanese"),
    Language("ptBR", "Portuguese (Brazil)"),
    Language("ptPT", "Portuguese (Portugal)"),
    Language("it", "Italian"),
    Language("ko", "Korean"),
    Language("pl", "Polish"),
    Language("nl", "Dutch"),
    Language("tr", "Turkish"),
    Language("vi", "Vietnamese"),
    Language("th", "Thai"),
    Language("id", "Indonesian"),
    Language("uk", "Ukrainian"),
    Language("hi", "Hindi"),
    Language("ar", "Arabic"),
    Language("el", "Greek"),
    Language("he", "Hebrew"),
    Language("cs", "Czech"),
    Language("sv", "Swedish"),
    Language("da", "Danish"),
    Language("fi", "Finnish"),
    Language("no", "Norwegian"),
)

/** AO3 rating filter. Ids are AO3 tag ids from the search form. */
enum class Rating(val id: String, val label: String) {
    NotRated("9", "Not Rated"),
    GeneralAudiences("10", "General Audiences"),
    TeenAndUp("11", "Teen And Up Audiences"),
    Mature("12", "Mature"),
    Explicit("13", "Explicit"),
}

/** Archive warnings. Display order puts the common ones first; ids are AO3's. */
enum class Warning(val id: String, val label: String) {
    CreatorChoseNotToUse("14", "Creator Chose Not To Use Archive Warnings"),
    NoArchiveWarningsApply("16", "No Archive Warnings Apply"),
    GraphicDepictionsOfViolence("17", "Graphic Depictions Of Violence"),
    MajorCharacterDeath("18", "Major Character Death"),
    RapeNonCon("19", "Rape/Non-Con"),
    Underage("20", "Underage Sex"),
}

/** Category filter (AO3's relationship categories). */
enum class Category(val id: String, val label: String) {
    Ff("116", "F/F"),
    Fm("22", "F/M"),
    Gen("21", "Gen"),
    Mm("23", "M/M"),
    Multi("2246", "Multi"),
    Other("24", "Other"),
}

/** Completion status. [value] is the `work_search[complete]` radio value. */
enum class Completion(val value: String?, val label: String) {
    Any(null, "All works"),
    Complete("T", "Complete only"),
    InProgress("F", "In progress only"),
}

/** Crossovers. [value] is the `work_search[crossover]` radio value. */
enum class Crossovers(val value: String?, val label: String) {
    Include(null, "Include"),
    Exclude("F", "Exclude"),
    Only("T", "Only crossovers"),
}

/**
 * "Updated" filter. AO3 does not take absolute dates here: its date syntax is relative, and
 * `<` means newer ("less than seven days ago"). Verified against the live help page.
 */
enum class UpdatedWithin(val value: String?, val label: String) {
    Any(null, "Any time"),
    Day("<24 hours", "Past 24 hours"),
    Week("<7 days", "Past week"),
    Month("<30 days", "Past month"),
    Year("<365 days", "Past year"),
}

/**
 * Sort column. [value] is the `work_search[sort_column]` option value; null means "let AO3
 * decide" (best match for a search, date updated for a tag), so the parameter is omitted.
 */
enum class SortColumn(val value: String?, val label: String) {
    BestMatch(null, "Best match"),
    Updated("revised_at", "Date updated"),
    Posted("created_at", "Date posted"),
    Kudos("kudos_count", "Kudos"),
    Hits("hits", "Hits"),
    Words("word_count", "Word count"),
    Comments("comments_count", "Comments"),
    Bookmarks("bookmarks_count", "Bookmarks"),
    Title("title_to_sort_on", "Title"),
    Creator("authors_to_sort_on", "Creator"),
}

enum class SortDirection(val value: String, val label: String) {
    Descending("desc", "Descending"),
    Ascending("asc", "Ascending"),
}

/**
 * Everything the search form can narrow by, as AO3's own parameters.
 *
 * Name fields ("Fandoms", "Additional tags", ...) take free text the way the site's form does;
 * ids are only used where AO3 itself uses ids (rating, warnings, categories, language).
 */
data class SearchFilters(
    val query: String = "",
    val title: String = "",
    val creators: String = "",
    val fandomNames: String = "",
    val relationshipNames: String = "",
    val characterNames: String = "",
    val freeformNames: String = "",
    val rating: Rating? = null,
    val warnings: Set<Warning> = emptySet(),
    val categories: Set<Category> = emptySet(),
    val completion: Completion = Completion.Any,
    val crossovers: Crossovers = Crossovers.Include,
    val singleChapterOnly: Boolean = false,
    val wordsFrom: Int? = null,
    val wordsTo: Int? = null,
    val languageId: String? = null,
    val updatedWithin: UpdatedWithin = UpdatedWithin.Any,
    val sortColumn: SortColumn = SortColumn.BestMatch,
    val sortDirection: SortDirection = SortDirection.Descending,
) {

    /** Nothing narrows the results. Sort order does not count. */
    val isUnfiltered: Boolean
        get() = query.isBlank() && title.isBlank() && creators.isBlank() &&
            fandomNames.isBlank() && relationshipNames.isBlank() && characterNames.isBlank() &&
            freeformNames.isBlank() &&
            rating == null && warnings.isEmpty() && categories.isEmpty() &&
            completion == Completion.Any && crossovers == Crossovers.Include &&
            !singleChapterOnly && wordsFrom == null && wordsTo == null &&
            languageId == null && updatedWithin == UpdatedWithin.Any

    /**
     * How many narrowing filters are set, for the badge on the filter button. The search terms
     * and the sort order do not count: they are visible without opening the sheet.
     */
    val filterCount: Int
        get() = listOf(
            title,
            creators,
            fandomNames,
            relationshipNames,
            characterNames,
            freeformNames,
        ).count { it.isNotBlank() } +
            (if (rating != null) 1 else 0) +
            warnings.size +
            categories.size +
            (if (completion != Completion.Any) 1 else 0) +
            (if (crossovers != Crossovers.Include) 1 else 0) +
            (if (singleChapterOnly) 1 else 0) +
            (if (wordsFrom != null || wordsTo != null) 1 else 0) +
            (if (languageId != null) 1 else 0) +
            (if (updatedWithin != UpdatedWithin.Any) 1 else 0)

    val hasFilters: Boolean get() = filterCount > 0

    /**
     * AO3's word count syntax: "1000-5000", ">1000", "<5000". A reversed range is swapped
     * rather than silently returning nothing.
     */
    fun wordCountParam(): String? {
        val from = wordsFrom
        val to = wordsTo
        return when {
            from != null && to != null -> "${minOf(from, to)}-${maxOf(from, to)}"
            from != null -> ">$from"
            to != null -> "<$to"
            else -> null
        }
    }

    /** Drop every narrowing filter but keep the search terms and the chosen sort. */
    fun cleared(): SearchFilters = SearchFilters(
        query = query,
        sortColumn = sortColumn,
        sortDirection = sortDirection,
    )
}
