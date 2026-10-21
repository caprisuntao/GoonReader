package app.goonreader.data.repo

import app.goonreader.data.cache.ChapterCache
import app.goonreader.data.network.RateLimiter
import app.goonreader.data.prefs.AppPreferences
import app.goonreader.data.prefs.AppSettings
import app.goonreader.work.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies settings that live outside Compose: the chapter cache cap, the request gap and the
 * periodic update job. Starts once from `GoonReaderApp` and re-applies on every change.
 */
@Singleton
class SettingsApplier @Inject constructor(
    private val preferences: AppPreferences,
    private val cache: ChapterCache,
    private val rateLimiter: RateLimiter,
    private val scheduler: UpdateScheduler,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastUpdateConfig: AppSettings.UpdateConfig? = null

    fun start() {
        scope.launch {
            preferences.settings.collect { settings ->
                cache.setMaxBytes(settings.cacheLimit.bytes)
                rateLimiter.interactiveMillis = settings.networkDelayMs
                // Only the update job depends on these; other toggles must not reschedule it.
                // The first emission of a process is app start, which must keep the existing
                // schedule; only a real change replaces the request.
                val previous = lastUpdateConfig
                lastUpdateConfig = settings.updateConfig
                if (previous != settings.updateConfig) {
                    scheduler.apply(settings.updateConfig, replace = previous != null)
                }
            }
        }
    }
}
