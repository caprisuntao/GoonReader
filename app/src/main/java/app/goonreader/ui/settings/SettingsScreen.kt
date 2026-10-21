package app.goonreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.data.prefs.Accent
import app.goonreader.data.prefs.CacheLimit
import app.goonreader.data.prefs.ThemeMode
import app.goonreader.data.prefs.UpdateInterval
import app.goonreader.ui.components.BackButton
import app.goonreader.ui.components.ScreenScaffold

/** PRD 9.8 settings. Every control writes straight to DataStore and applies on the next frame. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Settings",
        modifier = modifier,
        navigationIcon = { BackButton(onBack) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            SectionTitle("Appearance")
            ChipRow(
                options = ThemeMode.entries,
                selected = settings.themeMode,
                label = { it.label() },
                onSelect = { mode -> viewModel.update { it.copy(themeMode = mode) } },
            )
            ChipRow(
                options = Accent.entries,
                selected = settings.accent,
                label = { it.label },
                onSelect = { accent -> viewModel.update { it.copy(accent = accent) } },
            )

            SectionTitle("Library updates")
            ChoiceSetting(
                title = "Check for updates",
                current = settings.updateInterval,
                options = UpdateInterval.entries,
                label = { it.label },
                onSelect = { interval -> viewModel.update { it.copy(updateInterval = interval) } },
            )
            SwitchSetting("Wi-Fi only", settings.wifiOnlyUpdates) { value ->
                viewModel.update { it.copy(wifiOnlyUpdates = value) }
            }
            SwitchSetting("Charging only", settings.chargingOnlyUpdates) { value ->
                viewModel.update { it.copy(chargingOnlyUpdates = value) }
            }
            SwitchSetting("Download new chapters automatically", settings.autoDownloadNewChapters) { value ->
                viewModel.update { it.copy(autoDownloadNewChapters = value) }
            }

            SectionTitle("Downloads")
            SwitchSetting("Delete a download after reading it", settings.deleteAfterReading) { value ->
                viewModel.update { it.copy(deleteAfterReading = value) }
            }
            ChoiceSetting(
                title = "Download ahead",
                current = settings.downloadAhead,
                options = DOWNLOAD_AHEAD_OPTIONS,
                label = { "$it chapters" },
                onSelect = { count -> viewModel.update { it.copy(downloadAhead = count) } },
            )

            SectionTitle("Advanced")
            ChoiceSetting(
                title = "Chapter cache limit",
                current = settings.cacheLimit,
                options = CacheLimit.entries,
                label = { it.label },
                onSelect = { limit -> viewModel.update { it.copy(cacheLimit = limit) } },
            )
            SettingRow(title = "Clear chapter cache", onClick = viewModel::clearCache)
            ChoiceSetting(
                title = "Network delay",
                current = settings.networkDelayMs,
                options = NETWORK_DELAY_OPTIONS,
                label = { "${it / 1000} s" },
                onSelect = { delay -> viewModel.update { it.copy(networkDelayMs = delay) } },
            )
            Text(
                text = "AO3 is a donation-funded archive. The delay is the minimum gap between requests; " +
                    "background checks are always slower.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
    }
}

private val DOWNLOAD_AHEAD_OPTIONS = listOf(3, 5, 10)
private val NETWORK_DELAY_OPTIONS = listOf(1_000L, 2_000L, 3_000L, 5_000L)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchSetting(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A read-only row that opens a radio list; same pattern the filter sheet uses. */
@Composable
private fun <T> ChoiceSetting(
    title: String,
    current: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = label(current),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(option)
                                    open = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == current, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Close") }
            },
        )
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.System -> "System"
    ThemeMode.Light -> "Light"
    ThemeMode.Dark -> "Dark"
    ThemeMode.Amoled -> "AMOLED"
}
