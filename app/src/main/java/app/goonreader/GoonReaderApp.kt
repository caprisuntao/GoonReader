package app.goonreader

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * Everything heavy (database, HTTP client, caches) is created lazily by Hilt so cold
 * start stays under the 2 s budget from the PRD; this class deliberately does nothing
 * but register itself.
 */
@HiltAndroidApp
class GoonReaderApp : Application()
