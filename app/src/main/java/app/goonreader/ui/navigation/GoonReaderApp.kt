package app.goonreader.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.preferredFrameRate
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.goonreader.ui.bookmarks.BookmarksScreen
import app.goonreader.ui.browse.BrowseScreen
import app.goonreader.ui.categories.CategoriesScreen
import app.goonreader.ui.comments.CommentsScreen
import app.goonreader.ui.details.WorkDetailsScreen
import app.goonreader.ui.downloads.DownloadQueueScreen
import app.goonreader.ui.history.HistoryScreen
import app.goonreader.ui.library.LibraryScreen
import app.goonreader.ui.more.MoreScreen
import app.goonreader.ui.reader.ReaderScreen
import app.goonreader.ui.settings.SettingsScreen
import app.goonreader.ui.stats.StatisticsScreen
import app.goonreader.ui.storage.StorageScreen
import app.goonreader.ui.theme.Motion
import app.goonreader.ui.updates.UpdatesScreen

private val tabRoutes: Set<String> = TopLevelDestination.entries.mapTo(mutableSetOf()) { it.route }

/** True when both ends of a navigation are tabs, so the shell's fixed bar never slides. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes


/**
 * App shell: five tabs, one shared bar, per-tab state saved and restored.
 * Pushed screens hide the bar and handle their own back arrow.
 */
@Composable
fun GoonReaderApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    // Detail screens take the whole screen; the tab bar belongs to the five top-level tabs.
    val showBottomBar = currentRoute == null || TopLevelDestination.entries.any { it.route == currentRoute }
    // The reader draws edge to edge and handles its own insets, so the shell must not pad it.
    val isReader = currentRoute == Routes.READER

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Android 15+ can hold a window at 60 Hz unless the app asks for more; this is the
        // single request for every Compose animation and list in the shell. The reader's
        // WebView asks separately because it renders in its own view, not in a Compose layer.
        modifier = Modifier.preferredFrameRate(FrameRateCategory.High),
        bottomBar = {
            if (showBottomBar) {
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
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Library.route,
            modifier = if (isReader) Modifier else Modifier.padding(innerPadding),
            // Tabs cross-fade in place. Pushed screens come in from the right while the
            // screen underneath drifts back a little, which gives depth without a long wait.
            enterTransition = {
                if (betweenTabs()) {
                    fadeIn(tween(Motion.Fast, easing = Motion.EaseOut)) +
                        scaleIn(tween(Motion.Fast, easing = Motion.EaseOut), initialScale = 0.985f)
                } else {
                    slideInHorizontally(tween(Motion.Standard, easing = Motion.EaseOut)) { it / 6 } +
                        fadeIn(tween(Motion.Standard, easing = Motion.EaseOut))
                }
            },
            exitTransition = {
                when {
                    betweenTabs() -> fadeOut(tween(Motion.Fast, easing = Motion.EaseIn))
                    targetState.destination.route == Routes.READER ->
                        fadeOut(tween(Motion.Standard, easing = Motion.EaseIn))
                    else -> slideOutHorizontally(tween(Motion.Fast, easing = Motion.EaseIn)) { -it / 12 } +
                        fadeOut(tween(Motion.Fast, easing = Motion.EaseIn))
                }
            },
            popEnterTransition = {
                when {
                    betweenTabs() -> fadeIn(tween(Motion.Fast, easing = Motion.EaseOut))
                    initialState.destination.route == Routes.READER ->
                        fadeIn(tween(Motion.Standard, easing = Motion.EaseOut))
                    else -> slideInHorizontally(tween(Motion.Standard, easing = Motion.EaseOut)) { -it / 6 } +
                        fadeIn(tween(Motion.Standard, easing = Motion.EaseOut))
                }
            },
            popExitTransition = {
                if (betweenTabs()) {
                    fadeOut(tween(Motion.Fast, easing = Motion.EaseIn))
                } else {
                    slideOutHorizontally(tween(Motion.Fast, easing = Motion.EaseIn)) { it / 12 } +
                        fadeOut(tween(Motion.Fast, easing = Motion.EaseIn))
                }
            },
            // The system back gesture is predictive: navigation drives the pop animation from
            // its own gesture-tracked progress and ignores the pop specs above, falling back to
            // a fade/scale default. These are copies of those specs, so leaving a screen looks
            // the same however the user leaves it — arrow, back gesture or system back button.
            // The unused Int is the container size along the back direction; the slide lambdas
            // already receive the width they need. Per-destination overrides cannot be given here,
            // because `composable` has no predictive variants, so the reader's fade-only rules
            // (its own popEnter/popExit plus the global fade when it is one end of the pop) are
            // repeated below.
            predictivePopEnterTransition = { _ ->
                when {
                    betweenTabs() -> fadeIn(tween(Motion.Fast, easing = Motion.EaseOut))
                    // Off the reader the screen below fades in instead of sliding; back onto the
                    // reader (e.g. from its comments) fades it in, as its own popEnter does.
                    initialState.destination.route == Routes.READER ||
                        targetState.destination.route == Routes.READER ->
                        fadeIn(tween(Motion.Standard, easing = Motion.EaseOut))
                    else -> slideInHorizontally(tween(Motion.Standard, easing = Motion.EaseOut)) { -it / 6 } +
                        fadeIn(tween(Motion.Standard, easing = Motion.EaseOut))
                }
            },
            predictivePopExitTransition = { _ ->
                when {
                    betweenTabs() -> fadeOut(tween(Motion.Fast, easing = Motion.EaseIn))
                    // The reader leaving is its own fade, not a slide.
                    initialState.destination.route == Routes.READER ->
                        fadeOut(tween(Motion.Fast, easing = Motion.EaseIn))
                    else -> slideOutHorizontally(tween(Motion.Fast, easing = Motion.EaseIn)) { it / 12 } +
                        fadeOut(tween(Motion.Fast, easing = Motion.EaseIn))
                }
            },
        ) {
            composable(TopLevelDestination.Library.route) {
                LibraryScreen(onOpenWork = { workId -> navController.navigate(Routes.workDetails(workId)) })
            }
            composable(TopLevelDestination.Updates.route) {
                UpdatesScreen(
                    onOpenChapter = { workId, chapterId ->
                        navController.navigate(Routes.reader(workId, chapterId))
                    },
                )
            }
            composable(TopLevelDestination.History.route) {
                HistoryScreen(
                    onOpenChapter = { workId, chapterId ->
                        navController.navigate(Routes.reader(workId, chapterId))
                    },
                )
            }
            composable(TopLevelDestination.Browse.route) {
                BrowseScreen(onOpenWork = { workId -> navController.navigate(Routes.workDetails(workId)) })
            }
            composable(TopLevelDestination.More.route) {
                MoreScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenStorage = { navController.navigate(Routes.STORAGE) },
                    onOpenDownloads = { navController.navigate(Routes.DOWNLOAD_QUEUE) },
                    onOpenStatistics = { navController.navigate(Routes.STATISTICS) },
                )
            }
            composable(Routes.WORK_DETAILS) { entry ->
                val workId = entry.arguments?.getString("workId").orEmpty()
                WorkDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChapter = { chapterId -> navController.navigate(Routes.reader(workId, chapterId)) },
                    onOpenComments = { chapterId -> navController.navigate(Routes.comments(workId, chapterId)) },
                    onOpenBookmarks = { navController.navigate(Routes.bookmarks(workId)) },
                    onOpenInBrowser = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                )
            }
            composable(
                route = Routes.READER,
                arguments = listOf(
                    navArgument("chapterId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("progress") {
                        type = NavType.FloatType
                        defaultValue = -1f
                    },
                ),
                // The reader is full screen and edge to edge, so it fades in rather than
                // sliding under the status bar. Everything else is a normal push.
                enterTransition = { fadeIn(tween(Motion.Slow, easing = Motion.EaseOut)) },
                exitTransition = { fadeOut(tween(Motion.Standard, easing = Motion.EaseIn)) },
                popEnterTransition = { fadeIn(tween(Motion.Standard, easing = Motion.EaseOut)) },
                popExitTransition = { fadeOut(tween(Motion.Fast, easing = Motion.EaseIn)) },
            ) { entry ->
                val workId = entry.arguments?.getString("workId").orEmpty()
                ReaderScreen(
                    onBack = { navController.popBackStack() },
                    onOpenComments = { chapterId -> navController.navigate(Routes.comments(workId, chapterId)) },
                )
            }
            composable(Routes.BOOKMARKS) { entry ->
                val workId = entry.arguments?.getString("workId").orEmpty()
                BookmarksScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { chapterId, progress ->
                        navController.navigate(Routes.reader(workId, chapterId, progress))
                    },
                )
            }
            composable(Routes.COMMENTS) {
                CommentsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.STORAGE) {
                StorageScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DOWNLOAD_QUEUE) {
                DownloadQueueScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.STATISTICS) {
                StatisticsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
