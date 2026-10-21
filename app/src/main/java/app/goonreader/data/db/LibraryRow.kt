package app.goonreader.data.db

import androidx.room.Embedded

/**
 * Library list row: the work plus its reading progress, computed by SQLite so the list never
 * loads every chapter into memory.
 */
data class LibraryRow(
    @Embedded val work: WorkEntity,
    val readChapters: Int,
    val chapterCount: Int,
)
