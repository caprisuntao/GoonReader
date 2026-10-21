package app.goonreader.data.prefs

/**
 * Actions a tap zone can trigger (PRD 10.2). Names are persisted, so renaming one resets the
 * user's grid — that is acceptable while the app is pre-release.
 */
enum class ReaderAction(val label: String) {
    NextPage("Next page"),
    PrevPage("Previous page"),
    Menu("Menu"),
    NextChapter("Next chapter"),
    PrevChapter("Previous chapter"),
    ScrollUp("Scroll up"),
    ScrollDown("Scroll down"),
    None("Nothing"),
    ;

    companion object {
        fun fromName(name: String?): ReaderAction? = entries.firstOrNull { it.name == name }
    }
}

enum class ReaderTheme(val label: String) {
    Day("Day"),
    Night("Night"),
    Sepia("Sepia"),
    Amoled("AMOLED"),
    Custom("Custom"),
}

/** PRD 10.1: continuous scroll (P0) or horizontal pages (P1). */
enum class ReaderMode(val label: String) {
    Scroll("Scroll"),
    Paged("Paged"),
}

/** PRD 10.1 page-turn animations; neither is free with a WebView, see WebViewReaderEngine. */
enum class PageAnimation(val label: String) {
    None("None"),
    Slide("Slide"),
    Fade("Fade"),
}

/** Everything the reader renders with; persisted in DataStore (PRD 5). */
data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.Night,
    val mode: ReaderMode = ReaderMode.Scroll,
    val pageAnimation: PageAnimation = PageAnimation.Slide,
    val fontSizeSp: Float = 18f,
    val lineSpacing: Float = 1.55f,
    val paragraphSpacingEm: Float = 0.8f,
    val sideMarginDp: Int = 22,
    val verticalMarginDp: Int = 14,
    val justify: Boolean = false,
    val showSummary: Boolean = true,
    val showNotes: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** PRD 10.3: volume keys turn pages, off by default because many readers use them for volume. */
    val volumeKeys: Boolean = false,
    /** PRD 10.5: clock, battery and progress while the chrome is hidden. */
    val statusOverlay: Boolean = false,
    /** PRD 10.8: auto-scroll speed in dp per second. */
    val autoScrollSpeed: Int = 60,
    /** PRD 10.7: text-to-speech speed multiplier and voice name ("" = engine default). */
    val ttsSpeed: Float = 1f,
    val ttsVoice: String = "",
    /** PRD 10.4: background and text for [ReaderTheme.Custom], ARGB. */
    val customBackground: Int = 0xFF1B1612.toInt(),
    val customText: Int = 0xFFE6DCCF.toInt(),
    /** PRD 10.4: imported font file, empty for the system font. */
    val customFontPath: String = "",
    /** PRD 10.6: a chapter counts as read at 95% by default. */
    val markReadThreshold: Float = 0.95f,
    val tapZones: List<ReaderAction> = DEFAULT_TAP_ZONES,
) {

    fun withTapZone(index: Int, action: ReaderAction): ReaderSettings =
        copy(tapZones = tapZones.toMutableList().also { it[index] = action })

    companion object {
        /**
         * Row-major 3x3. v1 is scroll-only, so "previous/next" degrade to scrolling up and
         * down; the top row scrolls up and the bottom row scrolls down, as PRD 10.2 describes,
         * and the middle row opens the menu.
         */
        val DEFAULT_TAP_ZONES: List<ReaderAction> = listOf(
            ReaderAction.ScrollUp, ReaderAction.ScrollUp, ReaderAction.ScrollUp,
            ReaderAction.Menu, ReaderAction.Menu, ReaderAction.Menu,
            ReaderAction.ScrollDown, ReaderAction.ScrollDown, ReaderAction.ScrollDown,
        )

        /** Any stored grid that is not exactly nine known actions falls back to the default. */
        fun tapZonesFrom(stored: String?): List<ReaderAction> {
            val parsed = stored?.split(',')?.map { ReaderAction.fromName(it.trim()) }
            return parsed?.takeIf { zones -> zones.size == 9 && zones.all { it != null } }
                ?.filterNotNull()
                ?: DEFAULT_TAP_ZONES
        }

        fun tapZonesToString(zones: List<ReaderAction>): String = zones.joinToString(",") { it.name }
    }
}
