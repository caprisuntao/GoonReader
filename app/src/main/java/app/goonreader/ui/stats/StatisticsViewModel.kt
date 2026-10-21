package app.goonreader.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.goonreader.data.db.StatsDao
import app.goonreader.data.db.StatsRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(stats: StatsDao) : ViewModel() {

    val row: StateFlow<StatsRow> = stats.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsRow())
}

/** "3 h 12 min", "47 min", "0 min" — the same shape the History screen uses for a session. */
internal fun formatReadingTime(millis: Long): String {
    val minutes = (millis / 60_000).toInt().coerceAtLeast(0)
    if (minutes < 1) return "0 min"
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$hours h" else "$hours h $rest min"
}
