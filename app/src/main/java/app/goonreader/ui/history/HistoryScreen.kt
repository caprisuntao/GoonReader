package app.goonreader.ui.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.goonreader.ui.components.EmptyState
import app.goonreader.ui.components.ScreenScaffold

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    ScreenScaffold(title = "History", modifier = modifier) {
        EmptyState(
            icon = Icons.Outlined.History,
            title = "Nothing read yet",
            message = "Chapters you open are listed here, newest first. Incognito mode in More pauses this.",
        )
    }
}
