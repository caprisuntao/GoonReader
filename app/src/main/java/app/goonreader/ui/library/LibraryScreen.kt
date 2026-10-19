package app.goonreader.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.goonreader.ui.components.EmptyState
import app.goonreader.ui.components.ScreenScaffold

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    ScreenScaffold(title = "Library", modifier = modifier) {
        EmptyState(
            icon = Icons.Outlined.CollectionsBookmark,
            title = "Your library is empty",
            message = "Works you add from Browse live here, with reading progress and new chapters tracked for you.",
        )
    }
}
