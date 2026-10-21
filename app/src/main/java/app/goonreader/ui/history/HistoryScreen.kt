package app.goonreader.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.data.db.HistoryRow
import app.goonreader.ui.components.EmptyState
import app.goonreader.ui.components.ScreenScaffold
import app.goonreader.ui.components.dayLabel
import app.goonreader.ui.theme.GoonReaderTheme
import app.goonreader.ui.theme.animateListItem

@Composable
fun HistoryScreen(
    onOpenChapter: (workId: String, chapterId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        title = "History",
        modifier = modifier,
        actions = {
            IconButton(onClick = { confirmClear = true }, enabled = state.total > 0) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear history")
            }
        },
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            placeholder = { Text("Search history") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        if (state.days.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = if (state.query.isBlank()) "Nothing read yet" else "No matches",
                message = if (state.query.isBlank()) {
                    "Chapters you open are listed here, newest first. Incognito mode in More pauses this."
                } else {
                    "No history entry matches \"${state.query}\"."
                },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                state.days.forEach { day ->
                    item(key = "day-${day.date}") {
                        Text(
                            text = dayLabel(day.date),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = animateListItem()
                                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                        )
                    }
                    items(day.entries, key = { it.entry.id }) { row ->
                        HistoryRowItem(
                            row = row,
                            onClick = { onOpenChapter(row.entry.workId, row.entry.chapterId) },
                            onDelete = { viewModel.delete(row.entry.id) },
                            modifier = animateListItem(),
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("Every history entry is deleted. Reading progress and downloads are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearAll()
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HistoryRowItem(
    row: HistoryRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The divider belongs to the animated root, so deleting a row moves and fades as one piece.
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.workTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${historyLabel(row)} · ${readingTime(row.entry.readDurationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
    }
}

private fun historyLabel(row: HistoryRow): String {
    val number = row.chapterNumber ?: return "Chapter no longer listed"
    val title = row.chapterTitle?.trim().orEmpty()
    return if (title.isEmpty()) "Ch. $number" else "Ch. $number - $title"
}
