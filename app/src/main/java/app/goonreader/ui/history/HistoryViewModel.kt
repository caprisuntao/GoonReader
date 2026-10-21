package app.goonreader.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.goonreader.data.db.HistoryRow
import app.goonreader.data.repo.HistoryRepository
import app.goonreader.ui.components.toLocalDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HistoryDay(val date: LocalDate, val entries: List<HistoryRow>)

data class HistoryUiState(
    val days: List<HistoryDay> = emptyList(),
    val query: String = "",
    val total: Int = 0,
)

/** History (PRD 9.4): sessions grouped by day, searchable by work title, deletable per row. */
@HiltViewModel
class HistoryViewModel @Inject constructor(private val repository: HistoryRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<HistoryUiState> = combine(repository.observe(), query) { rows, search ->
        val filtered = if (search.isBlank()) {
            rows
        } else {
            rows.filter { it.workTitle.contains(search, ignoreCase = true) }
        }
        HistoryUiState(
            days = filtered
                .groupBy { it.entry.lastReadAt.toLocalDay() }
                .map { (date, entries) -> HistoryDay(date, entries) }
                .sortedByDescending { it.date },
            query = search,
            total = rows.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun delete(entryId: Long) = viewModelScope.launch { repository.delete(entryId) }

    fun clearAll() = viewModelScope.launch { repository.clear() }
}

/** "under a minute", "12 min", "1 h 5 min". */
internal fun readingTime(millis: Long): String = when {
    millis < 60_000L -> "under a minute"
    millis < 3_600_000L -> "${millis / 60_000} min"
    else -> "${millis / 3_600_000} h ${(millis % 3_600_000) / 60_000} min"
}
