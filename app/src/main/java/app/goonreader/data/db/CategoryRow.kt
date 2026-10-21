package app.goonreader.data.db

import androidx.room.Embedded

/** A user category with the number of library works assigned to it (PRD 9.2/9.7). */
data class CategoryRow(
    @Embedded val category: CategoryEntity,
    val workCount: Int,
)
