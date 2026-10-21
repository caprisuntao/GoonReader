package app.goonreader.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.BuildConfig
import app.goonreader.ui.components.ScreenScaffold
import app.goonreader.ui.reader.ReaderSettingsSheet
import app.goonreader.ui.theme.GoonReaderTheme

/** PRD 9.7. Support Us is deliberately absent; Statistics is the P2 entry that shipped. */
@Composable
fun MoreScreen(
    onOpenSettings: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    var showReaderSettings by remember { mutableStateOf(false) }

    ScreenScaffold(title = "More", modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ToggleRow(
                title = "Downloaded only",
                detail = "Library shows downloaded works; Browse stays offline",
                checked = settings.downloadedOnly,
                onChange = viewModel::setDownloadedOnly,
            )
            HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
            ToggleRow(
                title = "Incognito mode",
                detail = "Reading history is not written",
                checked = settings.incognito,
                onChange = viewModel::setIncognito,
            )
            HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))

            EntryRow(Icons.Outlined.CloudDownload, "Download queue", onOpenDownloads)
            EntryRow(Icons.Outlined.Label, "Categories", onOpenCategories)
            EntryRow(Icons.Outlined.BarChart, "Statistics", onOpenStatistics)
            EntryRow(Icons.Outlined.Storage, "Data and storage", onOpenStorage)
            EntryRow(Icons.Outlined.Settings, "Settings", onOpenSettings)
            EntryRow(Icons.Outlined.MenuBook, "Reader settings") { showReaderSettings = true }

            HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = "GoonReader ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "An unofficial reader for Archive of Our Own. Not affiliated with, endorsed by, " +
                        "or supported by the Organization for Transformative Works or AO3.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showReaderSettings) {
        ReaderSettingsSheet(
            settings = readerSettings,
            onChange = viewModel::updateReader,
            onDismiss = { showReaderSettings = false },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun EntryRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}
