package app.goonreader.ui.browse

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.goonreader.ui.components.EmptyState
import app.goonreader.ui.components.ScreenScaffold

@Composable
fun BrowseScreen(modifier: Modifier = Modifier) {
    ScreenScaffold(title = "Browse", modifier = modifier) {
        EmptyState(
            icon = Icons.Outlined.Explore,
            title = "Search Archive of Our Own",
            message = "Search by tag, fandom, author, or any words in a title and summary — with AO3's own filters.",
        )
    }
}
