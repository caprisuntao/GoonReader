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
import app.goonreader.data.prefs.Accent
import app.goonreader.data.prefs.ThemeMode

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
    accent: Accent = Accent.Orange,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Amoled -> true
    }

    val base = when {
        !dark -> LightColors
        themeMode == ThemeMode.Amoled -> AmoledColors
        else -> DarkColors
    }
    // The default keeps the hand-tuned palette the reference screenshots were matched against;
    // other accents derive the accent roles from their seed and leave the warm surfaces alone.
    val roles = if (accent == Accent.Orange) base else accentRoles(base, accent, dark)

    val scheme = if (dark) schemeFrom(roles) else lightSchemeFrom(roles)

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

/**
 * Replaces roles 0..7 (the accent family) from one seed. The PRD wants the palette derived
 * rather than hand-picked per screen; surfaces stay on the warm base so the app keeps its look.
 * `shortcut: full seed-to-scheme generation (surfaces included) is M8 polish.`
 */
private fun accentRoles(base: Array<Color>, accent: Accent, dark: Boolean): Array<Color> {
    val (hue, saturation, _) = Color(accent.argb).toHsl()
    val s = saturation.coerceIn(0.35f, 0.8f)
    val roles = base.copyOf()

    if (dark) {
        roles[0] = Color.hsl(hue, s, 0.74f)
        roles[1] = Color.hsl(hue, 0.45f, 0.14f)
        roles[2] = Color.hsl(hue, s.coerceAtMost(0.7f), 0.30f)
        roles[3] = Color.hsl(hue, 0.55f, 0.90f)
        roles[4] = Color.hsl(hue, 0.28f, 0.80f)
        roles[5] = Color.hsl(hue, 0.30f, 0.16f)
        roles[6] = Color.hsl(hue, 0.22f, 0.27f)
        roles[7] = Color.hsl(hue, 0.40f, 0.92f)
    } else {
        roles[0] = Color.hsl(hue, s, 0.38f)
        roles[1] = Color.White
        roles[2] = Color.hsl(hue, s.coerceAtMost(0.6f), 0.88f)
        roles[3] = Color.hsl(hue, 0.55f, 0.20f)
        roles[4] = Color.hsl(hue, 0.28f, 0.42f)
        roles[5] = Color.White
        roles[6] = Color.hsl(hue, 0.30f, 0.90f)
        roles[7] = Color.hsl(hue, 0.45f, 0.18f)
    }
    return roles
}

private fun Color.toHsl(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, lightness)

    val delta = max - min
    val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val hue = if (max == r) {
        ((g - b) / delta + if (g < b) 6f else 0f) / 6f
    } else if (max == g) {
        ((b - r) / delta + 2f) / 6f
    } else {
        ((r - g) / delta + 4f) / 6f
    }
    return Triple(hue, saturation, lightness)
}
