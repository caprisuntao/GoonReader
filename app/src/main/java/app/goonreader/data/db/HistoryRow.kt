package app.goonreader.data.db

import androidx.room.Embedded

/**
 * One History row with the work and chapter it points at, so the list renders without a query
 * per row. Chapter fields are nullable because a chapter can disappear from AO3 while the
 * history entry stays.
 */
data class HistoryRow(
    @Embedded val entry: HistoryEntity,
    val workTitle: String,
    val chapterNumber: Int?,
    val chapterTitle: String?,
)
