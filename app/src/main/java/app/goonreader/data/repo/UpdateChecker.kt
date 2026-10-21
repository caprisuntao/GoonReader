package app.goonreader.data.repo

import app.goonreader.data.download.DownloadManager
import app.goonreader.data.prefs.AppPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One update-check pass over the library (PRD 7.4/7.3).
 *
 * Automatic runs are capped and skip works checked within the configured interval; a manual run
 * is the user asking, so it looks at everything, in the same serial order. When "download new
 * chapters" is on, the chapters a check just added are queued for download.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val repository: WorkRepository,
    private val preferences: AppPreferences,
    private val downloads: DownloadManager,
) {

    data class Summary(val checked: Int, val updated: Int)

    suspend fun run(manual: Boolean): Summary {
        val settings = preferences.settings.first()
        val hours = settings.updateInterval.hours
        if (!manual && hours == null) return Summary(checked = 0, updated = 0)

        val due = repository.worksDueForCheck(
            // Every stored timestamp is below this, so a manual run matches the whole library.
            since = if (manual) Long.MAX_VALUE else System.currentTimeMillis() - hours!! * 3_600_000L,
            limit = if (manual) UNLIMITED else MAX_WORKS_PER_RUN,
        )

        var updated = 0
        due.forEach { work ->
            val outcome = repository.checkForUpdate(work.id, background = !manual)
            if (outcome is UpdateOutcome.Updated) {
                updated++
                if (settings.autoDownloadNewChapters && outcome.newChapterIds.isNotEmpty()) {
                    downloads.enqueue(work.id, outcome.newChapterIds)
                }
            }
        }
        return Summary(checked = due.size, updated = updated)
    }

    companion object {
        /** A background run must finish; the rest wait for the next one. */
        private const val MAX_WORKS_PER_RUN = 10
        private const val UNLIMITED = Int.MAX_VALUE
    }
}
