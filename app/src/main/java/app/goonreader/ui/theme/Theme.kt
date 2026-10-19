package app.goonreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** User-selectable appearance modes from PRD 9.8. */
enum class ThemeMode { System, Light, Dark, Amoled }

/**
 * Surfaces that Material 3's ColorScheme has no role for, but the shell needs
 * (for example the unselected nav label and the nav pill when it should read as
 * a filled container rather than an accent).
 */
data class GoonReaderColors(
    val navUnselected: Color,
    val navPill: Color,
    val divider: Color,
)

private val LocalGoonReaderColors = staticCompositionLocalOf {
    GoonReaderColors(
        navUnselected = DarkColors[15],
        navPill = DarkColors[2],
        divider = DarkColors[16],
    )
}

private fun schemeFrom(roles: Array<Color>): ColorScheme = darkColorScheme(
    primary = roles[0],
    onPrimary = roles[1],
    primaryContainer = roles[2],
    onPrimaryContainer = roles[3],
    secondary = roles[4],
    onSecondary = roles[5],
    secondaryContainer = roles[6],
    onSecondaryContainer = roles[7],
    background = roles[8],
    onBackground = roles[9],
    surface = roles[10],
    surfaceContainer = roles[11],
    surfaceContainerHigh = roles[12],
    surfaceContainerHighest = roles[13],
    onSurfaceVariant = roles[14],
    outline = roles[15],
    outlineVariant = roles[16],
    error = roles[17],
    onError = roles[18],
    onSurface = roles[19],
    // Remaining roles intentionally keep M3 defaults; nothing in the shell uses them yet.
)

private fun lightSchemeFrom(roles: Array<Color>): ColorScheme = lightColorScheme(
    primary = roles[0],
    onPrimary = roles[1],
    primaryContainer = roles[2],
    onPrimaryContainer = roles[3],
    secondary = roles[4],
    onSecondary = roles[5],
    secondaryContainer = roles[6],
    onSecondaryContainer = roles[7],
    background = roles[8],
    onBackground = roles[9],
    surface = roles[10],
    surfaceContainer = roles[11],
    surfaceContainerHigh = roles[12],
    surfaceContainerHighest = roles[13],
    onSurfaceVariant = roles[14],
    outline = roles[15],
    outlineVariant = roles[16],
    error = roles[17],
    onError = roles[18],
    onSurface = roles[19],
)

@Composable
fun GoonReaderTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Amoled -> true
    }

    val scheme = when {
        !dark -> lightSchemeFrom(LightColors)
        themeMode == ThemeMode.Amoled -> schemeFrom(AmoledColors)
        else -> schemeFrom(DarkColors)
    }

    val extras = GoonReaderColors(
        navUnselected = scheme.onSurfaceVariant,
        navPill = scheme.primaryContainer,
        divider = scheme.outlineVariant,
    )

    CompositionLocalProvider(LocalGoonReaderColors provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = GoonReaderTypography,
            content = content,
        )
    }
}

/** Access the shell-only colours: `GoonReaderTheme.colors.navPill`. */
object GoonReaderTheme {
    val colors: GoonReaderColors
        @Composable get() = LocalGoonReaderColors.current
}
