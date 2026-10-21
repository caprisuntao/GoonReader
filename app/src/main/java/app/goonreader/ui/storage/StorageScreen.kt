package app.goonreader.ui.storage

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.ui.components.BackButton
import app.goonreader.ui.components.ScreenScaffold
import app.goonreader.ui.theme.GoonReaderTheme

@Composable
fun StorageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDownloads by remember { mutableStateOf(false) }

    // SAF: the app never picks a path itself, so backups work on any storage provider.
    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    ScreenScaffold(
        title = "Data and storage",
        modifier = modifier,
        navigationIcon = { BackButton(onBack) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Storage used",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
            )
            StorageRow(
                title = "Library database",
                detail = "Metadata, chapters and progress",
                bytes = state.databaseBytes,
                actionLabel = null,
                onAction = {},
            )
            HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
            StorageRow(
                title = "Chapter cache",
                detail = "Recently read chapters, evicted when full",
                bytes = state.cacheBytes,
                actionLabel = "Clear",
                onAction = viewModel::clearCache,
            )
            HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
            StorageRow(
                title = "Downloads",
                detail = "Chapters you downloaded; never evicted",
                bytes = state.downloadBytes,
                actionLabel = "Clear",
                onAction = { confirmDownloads = true },
            )
            Text(
                text = "Clearing the cache only makes chapters load again. Clearing downloads deletes the " +
                    "offline copies and keeps your reading progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )

            HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
            Text(
                text = "Backup",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { exportBackup.launch("goonreader-backup.json") }) {
                    Text("Export library…")
                }
                TextButton(
                    onClick = {
                        importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                ) {
                    Text("Import…")
                }
            }
            Text(
                text = "The backup holds works, chapters, reading progress, categories and bookmarks " +
                    "as JSON. Download files stay on the device; an import re-marks a chapter as " +
                    "downloaded only when its file is still there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (confirmDownloads) {
        AlertDialog(
            onDismissRequest = { confirmDownloads = false },
            title = { Text("Clear downloads?") },
            text = { Text("Every downloaded chapter is deleted. They can be downloaded again later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDownloads = false
                        viewModel.clearDownloads()
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDownloads = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StorageRow(
    title: String,
    detail: String,
    bytes: Long,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
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
        Spacer(Modifier.width(8.dp))
        Text(
            text = Formatter.formatShortFileSize(context, bytes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
