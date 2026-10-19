package app.goonreader.ui.updates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.goonreader.ui.components.EmptyState
import app.goonreader.ui.components.ScreenScaffold

@Composable
fun UpdatesScreen(modifier: Modifier = Modifier) {
    ScreenScaffold(title = "Updates", modifier = modifier) {
        EmptyState(
            icon = Icons.Outlined.NewReleases,
            title = "No updates yet",
            message = "New chapters in your library show up here, grouped by day.",
        )
    }
}
