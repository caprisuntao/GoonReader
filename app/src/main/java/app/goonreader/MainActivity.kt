package app.goonreader

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.data.prefs.AppPreferences
import app.goonreader.data.prefs.AppSettings
import app.goonreader.ui.navigation.GoonReaderApp
import app.goonreader.ui.reader.VolumeKeyPager
import app.goonreader.ui.theme.GoonReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Android 15+ adaptive refresh rate: without a request the window is treated as a
        // "normal" (usually 60 Hz) client even on a 120 Hz panel. Touching the window may
        // temporarily boost it; Compose and the reader's WebView request High themselves.
        // Inside your Activity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = windowManager.defaultDisplay
            val currentMode = display.mode

            // Find the best mode: highest refresh rate with the same resolution
            val bestMode = display.supportedModes
                .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                .maxByOrNull { it.refreshRate }

            if (bestMode != null) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = bestMode.modeId
                }
            }
        }
        setContent {
            val settings by appPreferences.settings.collectAsStateWithLifecycle(AppSettings())
            GoonReaderTheme(themeMode = settings.themeMode, accent = settings.accent) {
                GoonReaderApp()
            }
        }
    }

    /**
     * PRD 10.3: the reader may take over the volume keys. Only a reader that enabled the option
     * registers a handler, and it clears it when the screen goes away.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleVolumeKey(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        handleVolumeKey(keyCode, event) || super.onKeyUp(keyCode, event)

    private fun handleVolumeKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) return false
        val pager = VolumeKeyPager.pager ?: return false
        if (event.repeatCount == 0) pager(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        return true
    }
}
