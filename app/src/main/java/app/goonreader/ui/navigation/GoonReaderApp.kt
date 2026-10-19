package app.goonreader.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.goonreader.ui.browse.BrowseScreen
import app.goonreader.ui.history.HistoryScreen
import app.goonreader.ui.library.LibraryScreen
import app.goonreader.ui.more.MoreScreen
import app.goonreader.ui.updates.UpdatesScreen

/**
 * App shell: five tabs, one shared bar, per-tab state saved and restored.
 * Detail screens (work details, reader) are pushed on top of this graph later and
 * hide the bar.
 */
@Composable
fun GoonReaderApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            GoonBottomBar(
                selectedRoute = currentRoute,
                onSelect = { destination ->
                    navController.navigate(destination.route) {
                        // Keep a single copy of each tab and restore its scroll state.
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Library.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.Library.route) { LibraryScreen() }
            composable(TopLevelDestination.Updates.route) { UpdatesScreen() }
            composable(TopLevelDestination.History.route) { HistoryScreen() }
            composable(TopLevelDestination.Browse.route) { BrowseScreen() }
            composable(TopLevelDestination.More.route) { MoreScreen() }
        }
    }
}
