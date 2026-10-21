package app.goonreader.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app")

/**
 * App-wide settings, one DataStore file. Reads clamp and default, so a half-written or older
 * file still opens with sane values.
 */
@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    val settings: Flow<AppSettings> = context.appDataStore.data
        .map { it.toAppSettings() }
        .distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { prefs ->
            prefs.writeAppSettings(transform(prefs.toAppSettings()))
        }
    }
}

/** Internal so tests can build a fake Preferences with the same AppKeys. */
internal object AppKeys {
    val THEME_MODE = stringPreferencesKey("themeMode")
    val ACCENT = stringPreferencesKey("accent")
    val INCOGNITO = booleanPreferencesKey("incognito")
    val DOWNLOADED_ONLY = booleanPreferencesKey("downloadedOnly")
    val UPDATE_INTERVAL = stringPreferencesKey("updateInterval")
    val WIFI_ONLY = booleanPreferencesKey("wifiOnlyUpdates")
    val CHARGING_ONLY = booleanPreferencesKey("chargingOnlyUpdates")
    val AUTO_DOWNLOAD = booleanPreferencesKey("autoDownloadNewChapters")
    val DELETE_AFTER_READING = booleanPreferencesKey("deleteAfterReading")
    val DOWNLOAD_AHEAD = intPreferencesKey("downloadAhead")
    val CACHE_LIMIT = stringPreferencesKey("cacheLimit")
    val NETWORK_DELAY = longPreferencesKey("networkDelayMs")
}

internal fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == this[AppKeys.THEME_MODE] } ?: defaults.themeMode,
        accent = Accent.entries.firstOrNull { it.name == this[AppKeys.ACCENT] } ?: defaults.accent,
        incognito = this[AppKeys.INCOGNITO] ?: defaults.incognito,
        downloadedOnly = this[AppKeys.DOWNLOADED_ONLY] ?: defaults.downloadedOnly,
        updateInterval = UpdateInterval.entries.firstOrNull { it.name == this[AppKeys.UPDATE_INTERVAL] }
            ?: defaults.updateInterval,
        wifiOnlyUpdates = this[AppKeys.WIFI_ONLY] ?: defaults.wifiOnlyUpdates,
        chargingOnlyUpdates = this[AppKeys.CHARGING_ONLY] ?: defaults.chargingOnlyUpdates,
        autoDownloadNewChapters = this[AppKeys.AUTO_DOWNLOAD] ?: defaults.autoDownloadNewChapters,
        deleteAfterReading = this[AppKeys.DELETE_AFTER_READING] ?: defaults.deleteAfterReading,
        downloadAhead = (this[AppKeys.DOWNLOAD_AHEAD] ?: defaults.downloadAhead).coerceIn(1, 20),
        cacheLimit = CacheLimit.entries.firstOrNull { it.name == this[AppKeys.CACHE_LIMIT] } ?: defaults.cacheLimit,
        networkDelayMs = (this[AppKeys.NETWORK_DELAY] ?: defaults.networkDelayMs).coerceIn(500L, 10_000L),
    )
}

internal fun MutablePreferences.writeAppSettings(settings: AppSettings) {
    this[AppKeys.THEME_MODE] = settings.themeMode.name
    this[AppKeys.ACCENT] = settings.accent.name
    this[AppKeys.INCOGNITO] = settings.incognito
    this[AppKeys.DOWNLOADED_ONLY] = settings.downloadedOnly
    this[AppKeys.UPDATE_INTERVAL] = settings.updateInterval.name
    this[AppKeys.WIFI_ONLY] = settings.wifiOnlyUpdates
    this[AppKeys.CHARGING_ONLY] = settings.chargingOnlyUpdates
    this[AppKeys.AUTO_DOWNLOAD] = settings.autoDownloadNewChapters
    this[AppKeys.DELETE_AFTER_READING] = settings.deleteAfterReading
    this[AppKeys.DOWNLOAD_AHEAD] = settings.downloadAhead
    this[AppKeys.CACHE_LIMIT] = settings.cacheLimit.name
    this[AppKeys.NETWORK_DELAY] = settings.networkDelayMs
}
