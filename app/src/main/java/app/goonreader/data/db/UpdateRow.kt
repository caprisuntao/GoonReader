package app.goonreader.data.db

import androidx.room.Embedded

/**
 * One row of the Updates feed: a chapter that a check found new, plus the work title it
 * belongs to, so the feed renders without a second query per row.
 */
data class UpdateRow(
    @Embedded val chapter: ChapterEntity,
    val workTitle: String,
)
