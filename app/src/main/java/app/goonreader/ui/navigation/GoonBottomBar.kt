package app.goonreader.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.goonreader.ui.theme.GoonReaderTheme
import app.goonreader.ui.theme.GoonReaderColors
import app.goonreader.ui.theme.Motion

/**
 * Hand-rolled bottom bar rather than Material 3's NavigationBar: the reference design
 * puts a filled pill behind the icon only (not a full-width indicator), keeps labels
 * visible at all times, and uses a hairline divider instead of an elevation shadow.
 */
@Composable
fun GoonBottomBar(
    selectedRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shell: GoonReaderColors = GoonReaderTheme.colors
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column {
            HorizontalDivider(color = shell.divider.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(66.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = destination.route == selectedRoute
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) shell.navPill else Color.Transparent,
                        animationSpec = tween(Motion.Fast, easing = Motion.EaseOut),
                        label = "navPill",
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            shell.navUnselected
                        },
                        animationSpec = tween(Motion.Fast, easing = Motion.EaseOut),
                        label = "navIcon",
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            shell.navUnselected
                        },
                        animationSpec = tween(Motion.Fast, easing = Motion.EaseOut),
                        label = "navLabel",
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.9f,
                        animationSpec = spring(
                            dampingRatio = 0.75f,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "navIconScale",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onSelect(destination) },
                            )
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(54.dp)
                                .height(29.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(pillColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.icon,
                                contentDescription = destination.label,
                                tint = iconColor,
                                modifier = Modifier
                                    .size(21.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = labelColor,
                        )
                    }
                }
            }
        }
    }
}
