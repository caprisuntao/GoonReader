package app.goonreader.data.db

/** Reading statistics (PRD 9.7). One row, computed by SQLite. */
data class StatsRow(
    val works: Int = 0,
    val chaptersRead: Int = 0,
    val readingMillis: Long = 0L,
    val bookmarks: Int = 0,
    val downloaded: Int = 0,
)
