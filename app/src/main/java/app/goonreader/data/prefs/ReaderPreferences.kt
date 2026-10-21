package app.goonreader.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(name = "reader")

/**
 * Reader appearance and behaviour, one DataStore file. Every read goes through
 * [toReaderSettings], which clamps and defaults, so a half-written or older file still opens.
 */
@Singleton
class ReaderPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    val settings: Flow<ReaderSettings> = context.readerDataStore.data
        .map { it.toReaderSettings() }
        .distinctUntilChanged()

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        context.readerDataStore.edit { prefs ->
            prefs.writeReaderSettings(transform(prefs.toReaderSettings()))
        }
    }
}

private object Keys {
    val THEME = stringPreferencesKey("theme")
    val MODE = stringPreferencesKey("mode")
    val PAGE_ANIMATION = stringPreferencesKey("pageAnimation")
    val FONT_SIZE = floatPreferencesKey("fontSizeSp")
    val LINE_SPACING = floatPreferencesKey("lineSpacing")
    val PARAGRAPH_SPACING = floatPreferencesKey("paragraphSpacingEm")
    val SIDE_MARGIN = intPreferencesKey("sideMarginDp")
    val VERTICAL_MARGIN = intPreferencesKey("verticalMarginDp")
    val JUSTIFY = booleanPreferencesKey("justify")
    val SHOW_SUMMARY = booleanPreferencesKey("showSummary")
    val SHOW_NOTES = booleanPreferencesKey("showNotes")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keepScreenOn")
    val VOLUME_KEYS = booleanPreferencesKey("volumeKeys")
    val STATUS_OVERLAY = booleanPreferencesKey("statusOverlay")
    val AUTO_SCROLL_SPEED = intPreferencesKey("autoScrollSpeed")
    val TTS_SPEED = floatPreferencesKey("ttsSpeed")
    val TTS_VOICE = stringPreferencesKey("ttsVoice")
    val CUSTOM_BACKGROUND = intPreferencesKey("customBackground")
    val CUSTOM_TEXT = intPreferencesKey("customText")
    val CUSTOM_FONT = stringPreferencesKey("customFontPath")
    val MARK_READ = floatPreferencesKey("markReadThreshold")
    val TAP_ZONES = stringPreferencesKey("tapZones")
}

internal fun Preferences.toReaderSettings(): ReaderSettings {
    val defaults = ReaderSettings()
    return ReaderSettings(
        theme = ReaderTheme.entries.firstOrNull { it.name == this[Keys.THEME] } ?: defaults.theme,
        mode = ReaderMode.entries.firstOrNull { it.name == this[Keys.MODE] } ?: defaults.mode,
        pageAnimation = PageAnimation.entries.firstOrNull { it.name == this[Keys.PAGE_ANIMATION] }
            ?: defaults.pageAnimation,
        fontSizeSp = (this[Keys.FONT_SIZE] ?: defaults.fontSizeSp).coerceIn(12f, 36f),
        lineSpacing = (this[Keys.LINE_SPACING] ?: defaults.lineSpacing).coerceIn(1f, 2.5f),
        paragraphSpacingEm = (this[Keys.PARAGRAPH_SPACING] ?: defaults.paragraphSpacingEm).coerceIn(0f, 2f),
        sideMarginDp = (this[Keys.SIDE_MARGIN] ?: defaults.sideMarginDp).coerceIn(0, 64),
        verticalMarginDp = (this[Keys.VERTICAL_MARGIN] ?: defaults.verticalMarginDp).coerceIn(0, 64),
        justify = this[Keys.JUSTIFY] ?: defaults.justify,
        showSummary = this[Keys.SHOW_SUMMARY] ?: defaults.showSummary,
        showNotes = this[Keys.SHOW_NOTES] ?: defaults.showNotes,
        keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
        volumeKeys = this[Keys.VOLUME_KEYS] ?: defaults.volumeKeys,
        statusOverlay = this[Keys.STATUS_OVERLAY] ?: defaults.statusOverlay,
        autoScrollSpeed = (this[Keys.AUTO_SCROLL_SPEED] ?: defaults.autoScrollSpeed).coerceIn(10, 300),
        ttsSpeed = (this[Keys.TTS_SPEED] ?: defaults.ttsSpeed).coerceIn(0.5f, 2f),
        ttsVoice = this[Keys.TTS_VOICE] ?: defaults.ttsVoice,
        customBackground = this[Keys.CUSTOM_BACKGROUND] ?: defaults.customBackground,
        customText = this[Keys.CUSTOM_TEXT] ?: defaults.customText,
        customFontPath = this[Keys.CUSTOM_FONT] ?: defaults.customFontPath,
        markReadThreshold = (this[Keys.MARK_READ] ?: defaults.markReadThreshold).coerceIn(0.5f, 1f),
        tapZones = ReaderSettings.tapZonesFrom(this[Keys.TAP_ZONES]),
    )
}

internal fun MutablePreferences.writeReaderSettings(settings: ReaderSettings) {
    this[Keys.THEME] = settings.theme.name
    this[Keys.MODE] = settings.mode.name
    this[Keys.PAGE_ANIMATION] = settings.pageAnimation.name
    this[Keys.FONT_SIZE] = settings.fontSizeSp
    this[Keys.LINE_SPACING] = settings.lineSpacing
    this[Keys.PARAGRAPH_SPACING] = settings.paragraphSpacingEm
    this[Keys.SIDE_MARGIN] = settings.sideMarginDp
    this[Keys.VERTICAL_MARGIN] = settings.verticalMarginDp
    this[Keys.JUSTIFY] = settings.justify
    this[Keys.SHOW_SUMMARY] = settings.showSummary
    this[Keys.SHOW_NOTES] = settings.showNotes
    this[Keys.KEEP_SCREEN_ON] = settings.keepScreenOn
    this[Keys.VOLUME_KEYS] = settings.volumeKeys
    this[Keys.STATUS_OVERLAY] = settings.statusOverlay
    this[Keys.AUTO_SCROLL_SPEED] = settings.autoScrollSpeed
    this[Keys.TTS_SPEED] = settings.ttsSpeed
    this[Keys.TTS_VOICE] = settings.ttsVoice
    this[Keys.CUSTOM_BACKGROUND] = settings.customBackground
    this[Keys.CUSTOM_TEXT] = settings.customText
    this[Keys.CUSTOM_FONT] = settings.customFontPath
    this[Keys.MARK_READ] = settings.markReadThreshold
    this[Keys.TAP_ZONES] = ReaderSettings.tapZonesToString(settings.tapZones)
}
