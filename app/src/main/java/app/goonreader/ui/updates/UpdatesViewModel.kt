package app.goonreader.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.goonreader.data.db.UpdateRow
import app.goonreader.data.download.DownloadManager
import app.goonreader.data.download.DownloadState
import app.goonreader.data.repo.UpdateChecker
import app.goonreader.data.repo.WorkRepository
import app.goonreader.ui.components.toLocalDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One new chapter in the Updates feed. */
data class UpdateEntry(
    val workId: String,
    val chapterId: String,
    val workTitle: String,
    val number: Int,
    val title: String,
    val isRead: Boolean,
    val isDownloaded: Boolean,
    val fetchedAt: Long,
)

data class UpdateDay(val date: LocalDate, val entries: List<UpdateEntry>)

data class UpdatesUiState(
    val days: List<UpdateDay> = emptyList(),
    val lastCheckedAt: Long? = null,
    val isRefreshing: Boolean = false,
)

/**
 * Updates feed (PRD 9.3): chapters that a check found new, grouped by the local calendar day.
 * The feed is derived from the database, so an update check, a read flag or a download all
 * show up without anyone pushing refresh.
 */
@HiltViewModel
class UpdatesViewModel @Inject constructor(
    repository: WorkRepository,
    private val checker: UpdateChecker,
    private val downloads: DownloadManager,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<UpdatesUiState> = combine(
        repository.observeUpdates(),
        repository.observeLastCheckedAt(),
        refreshing,
    ) { rows, lastCheckedAt, isRefreshing ->
        UpdatesUiState(days = rows.toDays(), lastCheckedAt = lastCheckedAt, isRefreshing = isRefreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdatesUiState())

    val downloadState: StateFlow<DownloadState> = downloads.state

    /** PRD 9.3 top-bar refresh: a full manual check, not the capped background pass. */
    fun refreshNow() = viewModelScope.launch {
        refreshing.value = true
        try {
            checker.run(manual = true)
        } finally {
            refreshing.value = false
        }
    }

    fun download(entry: UpdateEntry) {
        downloads.enqueue(entry.workId, listOf(entry.chapterId))
    }

    fun cancelDownload() = downloads.cancel()

    fun retryDownloads() = downloads.retryFailed()
}

/**
 * Group by the day the chapter was first seen, in the device's time zone. [fetchedAt] is an
 * instant, so it must not go through the date-only conversion used for AO3's published dates.
 */
internal fun List<UpdateRow>.toDays(zone: ZoneId = ZoneId.systemDefault()): List<UpdateDay> =
    groupBy { row -> row.chapter.fetchedAt?.toLocalDay(zone) ?: LocalDate.now(zone) }
        .map { (date, rows) -> UpdateDay(date, rows.map { it.toEntry() }) }
        .sortedByDescending { it.date }

private fun UpdateRow.toEntry() = UpdateEntry(
    workId = chapter.workId,
    chapterId = chapter.id,
    workTitle = workTitle,
    number = chapter.number,
    title = chapter.title,
    isRead = chapter.isRead,
    isDownloaded = chapter.isDownloaded,
    fetchedAt = chapter.fetchedAt ?: 0L,
)
