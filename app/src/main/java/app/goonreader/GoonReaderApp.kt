package app.goonreader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.goonreader.data.repo.SettingsApplier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Everything heavy (database, HTTP client, caches) is created lazily by Hilt so cold start
 * stays under the 2 s budget; the only eager work is applying persisted settings once, which
 * includes registering the periodic library check.
 */
@HiltAndroidApp
class GoonReaderApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsApplier: SettingsApplier

    /** WorkManager needs the Hilt factory to build [app.goonreader.work.UpdateWorker]. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        settingsApplier.start()
    }
}
