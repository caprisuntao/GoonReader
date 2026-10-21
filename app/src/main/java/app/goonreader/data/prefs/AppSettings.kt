package app.goonreader.data.prefs

/**
 * App-wide preferences (PRD 9.7/9.8), separate from the reader's own settings so changing an
 * update toggle never rewrites font sizes.
 */
enum class ThemeMode { System, Light, Dark, Amoled }

/** Accent presets. The default keeps the hand-tuned palette; the rest derive their accent roles. */
enum class Accent(val label: String, val argb: Long) {
    Orange("Orange", 0xFFB3612B),
    Rose("Rose", 0xFFB33A5B),
    Green("Green", 0xFF3F7A4F),
    Blue("Blue", 0xFF34618F),
    Violet("Violet", 0xFF6B4A8F),
    Amber("Amber", 0xFFA8791C),
    ;
}

/** PRD 9.8 library update interval. Null hours means "never automatically". */
enum class UpdateInterval(val hours: Int?, val label: String) {
    Manual(null, "Manual only"),
    SixHours(6, "Every 6 hours"),
    TwelveHours(12, "Every 12 hours"),
    Daily(24, "Daily"),
    Weekly(168, "Weekly"),
}

/** PRD 9.8 chapter cache limit. */
enum class CacheLimit(val bytes: Long, val label: String) {
    Fifty(50L * 1024 * 1024, "50 MB"),
    Hundred(100L * 1024 * 1024, "100 MB"),
    TwoHundred(200L * 1024 * 1024, "200 MB"),
    FiveHundred(500L * 1024 * 1024, "500 MB"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.Dark,
    val accent: Accent = Accent.Orange,
    /** Incognito pauses history writes (PRD 9.4/9.7). */
    val incognito: Boolean = false,
    /** Library shows only works with a downloaded chapter; Browse refuses to go online. */
    val downloadedOnly: Boolean = false,
    val updateInterval: UpdateInterval = UpdateInterval.TwelveHours,
    val wifiOnlyUpdates: Boolean = false,
    val chargingOnlyUpdates: Boolean = false,
    val autoDownloadNewChapters: Boolean = false,
    val deleteAfterReading: Boolean = false,
    /** PRD 9.8 download-ahead count, used by the details "Next N" action. */
    val downloadAhead: Int = 5,
    val cacheLimit: CacheLimit = CacheLimit.Hundred,
    /** Minimum gap between user-initiated AO3 requests, PRD 9.8 "network delay". */
    val networkDelayMs: Long = 2_000L,
) {
    /**
     * Only the values WorkManager's job depends on, so toggling a theme does not reschedule the
     * periodic check.
     */
    data class UpdateConfig(
        val interval: UpdateInterval,
        val wifiOnly: Boolean,
        val chargingOnly: Boolean,
    )

    val updateConfig: UpdateConfig get() = UpdateConfig(updateInterval, wifiOnlyUpdates, chargingOnlyUpdates)
}
