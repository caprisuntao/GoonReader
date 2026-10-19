package app.goonreader.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The five bottom-nav tabs, in the order fixed by PRD 9.1.
 * Adding a sixth tab is a product decision, not a refactor.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Library(
        route = "library",
        label = "Library",
        icon = Icons.Outlined.CollectionsBookmark,
        selectedIcon = Icons.Filled.CollectionsBookmark,
    ),
    Updates(
        route = "updates",
        label = "Updates",
        icon = Icons.Outlined.NewReleases,
        selectedIcon = Icons.Filled.NewReleases,
    ),
    History(
        route = "history",
        label = "History",
        icon = Icons.Outlined.History,
        selectedIcon = Icons.Filled.History,
    ),
    Browse(
        route = "browse",
        label = "Browse",
        icon = Icons.Outlined.Explore,
        selectedIcon = Icons.Filled.Explore,
    ),
    More(
        route = "more",
        label = "More",
        icon = Icons.Outlined.MoreHoriz,
        selectedIcon = Icons.Filled.MoreHoriz,
    ),
}
