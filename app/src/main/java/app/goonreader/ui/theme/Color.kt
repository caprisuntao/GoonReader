package app.goonreader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Warm dark-brown palette, taken from the Mihon reference screenshots.
 *
 * Per the PRD the palette is derived from one orange-brown seed rather than
 * hand-tuned per screen. The seed lives here and every role below is a tonal
 * step away from it; the accent-colour setting in M6 will replace the seed at
 * runtime and regenerate these roles.
 */
val SeedOrangeBrown = Color(0xFFB3612B)

// --- Dark (default) -------------------------------------------------------
internal val DarkColors = arrayOf(
    Color(0xFFF2B48F), // 0 primary
    Color(0xFF4A2410), // 1 onPrimary
    Color(0xFF6E4B2E), // 2 primaryContainer: the nav pill in the reference
    Color(0xFFFFDBC7), // 3 onPrimaryContainer
    Color(0xFFE0C4B0), // 4 secondary
    Color(0xFF3F2C20), // 5 onSecondary
    Color(0xFF574437), // 6 secondaryContainer
    Color(0xFFFDE1CD), // 7 onSecondaryContainer
    Color(0xFF191110), // 8 background
    Color(0xFFF0E0D6), // 9 onBackground
    Color(0xFF1E1512), // 10 surface
    Color(0xFF251B18), // 11 surfaceContainer
    Color(0xFF30231F), // 12 surfaceContainerHigh
    Color(0xFF3A2C27), // 13 surfaceContainerHighest
    Color(0xFFD5C3B6), // 14 onSurfaceVariant
    Color(0xFF9C8C80), // 15 outline
    Color(0xFF52443A), // 16 outlineVariant
    Color(0xFFFFB4AB), // 17 error
    Color(0xFF690005), // 18 onError
    Color(0xFFF0E0D6), // 19 onSurface
)

// --- AMOLED ---------------------------------------------------------------
// Same hues, but every surface is true black so OLED pixels switch off.
internal val AmoledColors = DarkColors.copyOf().also { c ->
    c[8] = Color(0xFF000000) // background
    c[10] = Color(0xFF000000) // surface
    c[11] = Color(0xFF0B0706) // surfaceContainer
    c[12] = Color(0xFF121010) // surfaceContainerHigh
    c[13] = Color(0xFF1A1614) // surfaceContainerHighest
}

// --- Light ----------------------------------------------------------------
internal val LightColors = arrayOf(
    Color(0xFF8B4A21), // primary
    Color(0xFFFFFFFF), // onPrimary
    Color(0xFFFFDBC7), // primaryContainer
    Color(0xFF3A1200), // onPrimaryContainer
    Color(0xFF73543F), // secondary
    Color(0xFFFFFFFF), // onSecondary
    Color(0xFFF5DCC9), // secondaryContainer
    Color(0xFF2B160A), // onSecondaryContainer
    Color(0xFFFFF8F4), // background
    Color(0xFF231A15), // onBackground
    Color(0xFFFFF8F4), // surface
    Color(0xFFF7EBE3), // surfaceContainer
    Color(0xFFF1E2D8), // surfaceContainerHigh
    Color(0xFFEADACF), // surfaceContainerHighest
    Color(0xFF53443A), // onSurfaceVariant
    Color(0xFF85746A), // outline
    Color(0xFFD8C2B4), // outlineVariant
    Color(0xFFBA1A1A), // error
    Color(0xFFFFFFFF), // onError
    Color(0xFF231A15), // onSurface
)
