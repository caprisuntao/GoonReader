package app.goonreader.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.goonreader.data.repo.UpdateChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Periodic library update check (PRD 7.3/7.4). All the work — capping, skipping recent checks,
 * pacing against the shared rate limiter — lives in [UpdateChecker]; this only wires WorkManager
 * to it.
 */
@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val checker: UpdateChecker,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val summary = checker.run(manual = false)
            Log.d(TAG, "checked ${summary.checked} works, ${summary.updated} updated")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The next periodic run comes around anyway; retrying now would hammer AO3.
            Log.w(TAG, "update check aborted", e)
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "GoonReader"
    }
}
