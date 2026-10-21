package app.goonreader.ui.updates

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.data.download.DownloadState
import app.goonreader.ui.components.EmptyState
import app.goonreader.ui.components.ScreenScaffold
import app.goonreader.ui.components.dayLabel
import app.goonreader.ui.theme.GoonReaderTheme
import app.goonreader.ui.theme.animateListItem
import java.time.LocalDate

/**
 * Updates (PRD 9.3): new chapters detected by update checks, grouped by day, with a manual
 * "check now" in the top bar. Filter and calendar jump are not built yet.
 */
@Composable
fun UpdatesScreen(
    onOpenChapter: (workId: String, chapterId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val download by viewModel.downloadState.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Updates",
        modifier = modifier,
        actions = {
            if (state.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = viewModel::refreshNow) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Check for updates")
                }
            }
        },
    ) {
        Text(
            text = "Library last updated: ${lastCheckedLabel(state.lastCheckedAt)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        )

        if (state.days.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.NewReleases,
                title = "No updates yet",
                message = "New chapters in your library show up here, grouped by day.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                state.days.forEach { day ->
                    item(key = "day-${day.date}") { DayHeader(day.date, animateListItem()) }
                    items(day.entries, key = { it.chapterId }) { entry ->
                        UpdateRowItem(
                            entry = entry,
                            download = download,
                            onOpen = { onOpenChapter(entry.workId, entry.chapterId) },
                            onDownload = { viewModel.download(entry) },
                            modifier = animateListItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, modifier: Modifier = Modifier) {
    Text(
        text = dayLabel(date),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun UpdateRowItem(
    entry: UpdateEntry,
    download: DownloadState,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One animated root, divider included, so a row never separates from its line.
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnreadDot(visible = !entry.isRead)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.workTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = chapterLabel(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DownloadAction(entry = entry, download = download, onDownload = onDownload)
        }
        HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
    }
}

/** PRD 9.3: a check when the chapter is downloaded, progress while that chapter is in the queue. */
@Composable
private fun DownloadAction(
    entry: UpdateEntry,
    download: DownloadState,
    onDownload: () -> Unit,
) {
    when {
        entry.isDownloaded -> Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = "Downloaded",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .size(20.dp),
        )

        download.running && download.currentChapterId == entry.chapterId -> CircularProgressIndicator(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .size(18.dp),
            strokeWidth = 2.dp,
        )

        else -> IconButton(onClick = onDownload, enabled = !download.running) {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = "Download chapter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnreadDot(visible: Boolean) {
    if (!visible) {
        // Keep the text aligned whether or not the row is unread.
        Spacer(Modifier.width(6.dp))
        return
    }
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

internal fun chapterLabel(entry: UpdateEntry): String {
    val title = entry.title.trim()
    return if (title.isEmpty()) "Ch. ${entry.number}" else "Ch. ${entry.number} - $title"
}

private fun lastCheckedLabel(lastCheckedAt: Long?): String = lastCheckedAt?.let {
    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
} ?: "never"
