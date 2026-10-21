package app.goonreader.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.data.db.BookmarkEntity
import app.goonreader.ui.components.BackButton
import app.goonreader.ui.components.EmptyState
import app.goonreader.ui.components.ScreenScaffold
import app.goonreader.ui.theme.animateListItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val SAVED_ON = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** Saved positions for one work, newest position first per chapter (PRD 10.9). */
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpen: (chapterId: String, progress: Float) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Bookmarks",
        navigationIcon = { BackButton(onBack) },
    ) {
        if (rows.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.BookmarkBorder,
                title = "No bookmarks yet",
                message = "Tap the bookmark icon in the reader to save a position in a chapter.",
            )
        } else {
            LazyColumn {
                items(rows, key = { it.id }) { bookmark ->
                    Column(animateListItem()) {
                        BookmarkRow(
                            bookmark = bookmark,
                            onOpen = { onOpen(bookmark.chapterId, bookmark.progress) },
                            onDelete = { viewModel.delete(bookmark.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Chapter ${bookmark.chapterNumber} · ${(bookmark.progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = bookmark.chapterTitle.ifBlank { "Chapter ${bookmark.chapterNumber}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Saved " + SAVED_ON.format(
                    Instant.ofEpochMilli(bookmark.createdAt).atZone(ZoneId.systemDefault()),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete bookmark")
        }
    }
}
