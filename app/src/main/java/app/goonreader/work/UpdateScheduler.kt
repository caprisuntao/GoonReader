package app.goonreader.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.goonreader.data.prefs.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the periodic update job. The interval and constraints are settings (PRD 9.8), so this
 * applies them rather than hardcoding one schedule.
 *
 * [replace] distinguishes a settings change (UPDATE the existing request) from app start (KEEP),
 * so relaunching the app never pushes the next check back.
 */
@Singleton
class UpdateScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    fun apply(config: AppSettings.UpdateConfig, replace: Boolean) {
        val workManager = WorkManager.getInstance(context)
        val hours = config.interval.hours
        if (hours == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (config.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(config.chargingOnly)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val WORK_NAME = "library-update-check"
    }
}
