package app.goonreader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier

/**
 * The app's motion vocabulary.
 *
 * Reading is the job, so movement is short and decelerating rather than bouncy: nothing here
 * should delay a tap or make a screen feel like it is loading twice. Durations are in
 * milliseconds and are used by the navigation transitions, the shell's selection indicator,
 * the reader's chrome and every lazy list.
 */
object Motion {
    /** Small, direct changes: icon selection, fades, a row leaving. */
    const val Fast = 140

    /** Moving a whole surface: screen pushes, bars sliding in. */
    const val Standard = 200

    /** A full-screen entrance where a little extra time reads as intent, not lag. */
    const val Slow = 280

    /** Deceleration. The default for anything arriving on screen. */
    val EaseOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Acceleration. Used for the surface leaving as a new one arrives. */
    val EaseIn: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}

/**
 * Lazy items settle into their new position instead of teleporting when the data behind them
 * changes (a chapter marked read, a download queued, a row deleted). Call from an item scope.
 */
fun LazyItemScope.animateListItem(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(Motion.Fast, easing = Motion.EaseOut),
    placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
    fadeOutSpec = tween(Motion.Fast, easing = Motion.EaseIn),
)
