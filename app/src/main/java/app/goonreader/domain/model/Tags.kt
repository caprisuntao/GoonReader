package app.goonreader.domain.model

/**
 * A tag as listed by AO3's tag search.
 *
 * [href] is copied verbatim from the page: AO3 escapes special characters inside tag names
 * (`/` as `*s*`, `&` as `*a*`, `.` as `*d*`, ...), so building a tag URL by hand produces 404s.
 */
data class TagRef(
    val name: String,
    val type: TagType,
    val href: String,
    /** Works carrying the tag, when AO3 printed a count. */
    val workCount: Int? = null,
)

/** Tag categories the tag search can filter by. [ao3Value] is the `tag_search[type]` value. */
enum class TagType(val ao3Value: String?, val label: String) {
    Any(null, "All types"),
    Fandom("Fandom", "Fandom"),
    Character("Character", "Character"),
    Relationship("Relationship", "Relationship"),
    Freeform("Freeform", "Tag"),
    Unsorted("UnsortedTag", "Unsorted tag"),
    Unknown("Unknown", "Tag");

    companion object {
        /** Maps AO3's label ("Freeform", "UnsortedTag", ...) onto the enum. */
        fun fromLabel(label: String): TagType =
            entries.firstOrNull { it.ao3Value.equals(label, ignoreCase = true) } ?: Unknown
    }
}
