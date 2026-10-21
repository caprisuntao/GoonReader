package app.goonreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.goonreader.data.tts.TtsState
import app.goonreader.data.tts.TtsStatus

/**
 * Read-aloud controls (PRD 10.7). Starting, speed, voice, paragraph stepping and the sleep timer
 * live here; the notification repeats the transport controls while the screen is off.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TtsSheet(
    state: TtsState,
    onStart: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStop: () -> Unit,
    onSpeed: (Float) -> Unit,
    onVoice: (String) -> Unit,
    onSleep: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Read aloud", style = MaterialTheme.typography.titleMedium)

            when (state.status) {
                TtsStatus.Idle -> {
                    Text(
                        text = "Reads this chapter and the ones after it, with a notification you can control.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text("Start from this chapter")
                    }
                }

                TtsStatus.Error -> {
                    Text(
                        text = state.error ?: "Playback failed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                }

                TtsStatus.Preparing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Loading the chapter…", style = MaterialTheme.typography.bodyMedium)
                }

                TtsStatus.Playing, TtsStatus.Paused -> {
                    Text(
                        text = state.chapterLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPrevious) {
                            Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous paragraph")
                        }
                        IconButton(onClick = onToggle) {
                            Icon(
                                imageVector = if (state.status == TtsStatus.Playing) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = if (state.status == TtsStatus.Playing) "Pause" else "Play",
                            )
                        }
                        IconButton(onClick = onNext) {
                            Icon(Icons.Outlined.SkipNext, contentDescription = "Next paragraph")
                        }
                    }
                    Text(
                        text = "Paragraph ${state.paragraph + 1} of ${state.paragraphCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Speed", style = MaterialTheme.typography.bodyMedium)
                Text("${"%.1f".format(state.speed)}×", style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = state.speed,
                onValueChange = onSpeed,
                valueRange = 0.5f..2f,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Voice", style = MaterialTheme.typography.bodyMedium)
            if (state.voices.isEmpty()) {
                Text(
                    text = "Voices appear once playback starts.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.voice.isEmpty(),
                        onClick = { onVoice("") },
                        label = { Text("System default") },
                    )
                    state.voices.take(12).forEach { voice ->
                        FilterChip(
                            selected = state.voice == voice.name,
                            onClick = { onVoice(voice.name) },
                            label = { Text(voice.label) },
                        )
                    }
                }
            }

            Text("Sleep timer", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.sleepMinutes == null,
                    onClick = { onSleep(null) },
                    label = { Text("Off") },
                )
                listOf(5, 15, 30).forEach { minutes ->
                    FilterChip(
                        selected = state.sleepMinutes == minutes,
                        onClick = { onSleep(minutes) },
                        label = { Text("$minutes min") },
                    )
                }
            }

            if (state.isActive) {
                TextButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop reading aloud")
                }
            }
        }
    }
}
