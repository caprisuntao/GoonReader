package app.goonreader.ui.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.ui.components.BackButton
import app.goonreader.ui.components.EmptyState
import app.goonreader.ui.components.ScreenScaffold

/** PRD 9.9 in its smallest honest form: progress, cancel and retry for the one running batch. */
@Composable
fun DownloadQueueScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadQueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Download queue",
        modifier = modifier,
        navigationIcon = { BackButton(onBack) },
    ) {
        if (!state.running && !state.hasFailures) {
            EmptyState(
                icon = Icons.Outlined.CloudDownload,
                title = "Nothing queued",
                message = "Start a download from a work's details screen or from an Updates entry.",
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (state.running) {
                    Text(
                        text = "Downloading ${state.processed} of ${state.total} chapters",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (state.total == 0) 0f else state.processed / state.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::cancel) { Text("Cancel") }
                    }
                }
                if (state.hasFailures) {
                    Text(
                        text = "${state.failed.size} chapters could not be downloaded",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = viewModel::retry) { Text("Retry") }
                }
            }
        }
        Text(
            text = "Downloads run while the app is open. Closing the app stops a batch; " +
                "chapters already saved stay downloaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
