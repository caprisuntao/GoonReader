package app.goonreader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.data.db.LibraryRow
import app.goonreader.data.db.WorkEntity
import app.goonreader.ui.components.EmptyState
import app.goonreader.ui.components.ScreenScaffold
import app.goonreader.ui.theme.GoonReaderTheme
import app.goonreader.ui.theme.animateListItem

@Composable
fun LibraryScreen(
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        title = "Library",
        modifier = modifier,
        actions = {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Open a work")
            }
        },
    ) {
        if (state.categories.isNotEmpty()) {
            CategoryTabs(
                state = state,
                onSelect = viewModel::selectCategory,
            )
        }

        if (state.rows.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.CollectionsBookmark,
                title = if (state.downloadedOnly) "No downloaded works" else "Your library is empty",
                message = if (state.downloadedOnly) {
                    "Downloaded only is on. Turn it off in More, or download chapters from a work's details."
                } else {
                    "Open a work with the + button, or find one in Browse."
                },
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.rows, key = { it.work.id }) { row ->
                    LibraryRowItem(
                        row = row,
                        onClick = { onOpenWork(row.work.id) },
                        modifier = animateListItem(),
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddWorkDialog(
            onDismiss = { showAddDialog = false },
            onOpen = { workId ->
                showAddDialog = false
                onOpenWork(workId)
            },
        )
    }
}

/** PRD 9.2: one tab per category, hidden entirely when the user has none. */
@Composable
private fun CategoryTabs(state: LibraryUiState, onSelect: (Long?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.selectedCategoryId == null,
            onClick = { onSelect(null) },
            label = { Text("All") },
        )
        state.categories.forEach { category ->
            FilterChip(
                selected = state.selectedCategoryId == category.category.id,
                onClick = { onSelect(category.category.id) },
                label = { Text(category.category.name) },
            )
        }
    }
}

@Composable
private fun LibraryRowItem(row: LibraryRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val work = row.work
    val unread = (row.chapterCount - row.readChapters).coerceAtLeast(0)

    // The divider lives inside the animated root so the whole row moves together when the
    // list is reordered or an item is removed.
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = work.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (work.isComplete) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle(work),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${row.readChapters}/${row.chapterCount} chapters",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (unread > 0) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "$unread unread",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
    }
}

/** "Author · First fandom", skipping whichever is missing. */
private fun subtitle(work: WorkEntity): String =
    listOf(
        work.authors.lineSequence().firstOrNull().orEmpty().trim(),
        work.fandoms.lineSequence().firstOrNull().orEmpty().trim(),
    ).filter { it.isNotEmpty() }.joinToString(" · ")

@Composable
private fun AddWorkDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val workId = remember(input) { parseWorkId(input) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open a work") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Link or work id") },
                    placeholder = { Text("archiveofourown.org/works/4701869") },
                    singleLine = true,
                    isError = input.isNotBlank() && workId == null,
                )
                if (input.isNotBlank() && workId == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "That is not an AO3 work link or id.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { workId?.let(onOpen) }, enabled = workId != null) {
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
