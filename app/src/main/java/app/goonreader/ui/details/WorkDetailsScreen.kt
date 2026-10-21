package app.goonreader.ui.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.data.db.CategoryRow
import app.goonreader.data.download.DownloadState
import app.goonreader.data.network.Ao3Urls
import app.goonreader.data.repo.FailureReason
import app.goonreader.domain.model.Work
import app.goonreader.ui.components.FailureState
import app.goonreader.ui.components.ScreenScaffold
import app.goonreader.ui.theme.Motion
import app.goonreader.ui.theme.animateListItem
import org.jsoup.Jsoup
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CHAPTER_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** Which of the screen's three mutually exclusive bodies is showing. */
private enum class DetailsPhase { Loading, Failure, Content }

@Composable
fun WorkDetailsScreen(
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String) -> Unit,
    onOpenComments: (chapterId: String) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val download by viewModel.downloadState.collectAsStateWithLifecycle()
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    val assignedCategories by viewModel.assignedCategoryIds.collectAsStateWithLifecycle()
    val downloadAhead by viewModel.downloadAhead.collectAsStateWithLifecycle()
    var showCategories by remember { mutableStateOf(false) }
    val openInBrowser = { onOpenInBrowser(Ao3Urls.work(viewModel.workId)) }

    // Read state changes while the reader is open; refresh the list when coming back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }

    ScreenScaffold(
        title = state.work?.title ?: "Work",
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onOpenBookmarks) {
                Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Bookmarks")
            }
            IconButton(onClick = { showCategories = true }) {
                Icon(Icons.Outlined.Label, contentDescription = "Categories")
            }
            IconButton(onClick = { openInBrowser() }) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = "Open in browser")
            }
        },
    ) {
        val failure = state.failure
        val phase = when {
            state.isLoading -> DetailsPhase.Loading
            failure != null -> DetailsPhase.Failure
            state.work != null -> DetailsPhase.Content
            else -> DetailsPhase.Loading
        }
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (fadeIn(tween(Motion.Standard, easing = Motion.EaseOut)) +
                    slideInVertically(tween(Motion.Standard, easing = Motion.EaseOut)) { it / 12 })
                    .togetherWith(fadeOut(tween(Motion.Fast, easing = Motion.EaseIn)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "detailsPhase",
        ) { current ->
            when (current) {
                DetailsPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                DetailsPhase.Failure -> FailureState(
                    reason = failure ?: FailureReason.HttpError,
                    onRetry = viewModel::retry,
                    secondaryAction = {
                        OutlinedButton(onClick = { openInBrowser() }) { Text("Open in browser") }
                    },
                )
                DetailsPhase.Content -> state.work?.let { work ->
                    WorkBody(
                        workId = viewModel.workId,
                        work = work,
                        state = state,
                        download = download,
                        downloadAhead = downloadAhead,
                        onAdd = viewModel::addToLibrary,
                        onRemove = viewModel::removeFromLibrary,
                        onRefresh = viewModel::refresh,
                        onDownloadAll = viewModel::downloadAll,
                        onDownloadUnread = viewModel::downloadUnread,
                        onDownloadNext = viewModel::downloadNext,
                        onDeleteDownloads = viewModel::deleteDownloads,
                        onCancelDownloads = viewModel::cancelDownloads,
                        onRetryDownloads = viewModel::retryDownloads,
                        onOpenChapter = onOpenChapter,
                        onOpenComments = onOpenComments,
                    )
                }
            }
        }
    }

    if (showCategories) {
        CategoryPickerDialog(
            categories = categories,
            assigned = assignedCategories,
            onToggle = viewModel::toggleCategory,
            onDismiss = { showCategories = false },
        )
    }
}

@Composable
private fun WorkBody(
    workId: String,
    work: Work,
    state: WorkDetailsUiState,
    download: DownloadState,
    downloadAhead: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadAll: () -> Unit,
    onDownloadUnread: () -> Unit,
    onDownloadNext: (Int) -> Unit,
    onDeleteDownloads: () -> Unit,
    onCancelDownloads: () -> Unit,
    onRetryDownloads: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenComments: (String) -> Unit,
) {
    val downloadedCount = state.chapters.count { it.isDownloaded }
    val pendingCount = state.chapters.count { !it.isDownloaded }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { StatsLine(work) }
        item { MetaBlock(work) }

        val summary = work.summaryHtml
        if (!summary.isNullOrBlank()) {
            item {
                Text(
                    // Details shows summary as plain text; the reader renders the real HTML.
                    text = Jsoup.parse(summary).text(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }

        item {
            ActionsRow(
                inLibrary = state.inLibrary,
                downloadBusy = download.running,
                pendingCount = pendingCount,
                downloadedCount = downloadedCount,
                nextCount = downloadAhead,
                onAdd = onAdd,
                onRemove = onRemove,
                onRefresh = onRefresh,
                onDownloadAll = onDownloadAll,
                onDownloadUnread = onDownloadUnread,
                onDownloadNext = { onDownloadNext(downloadAhead) },
                onDeleteDownloads = onDeleteDownloads,
            )
        }
        if (state.inLibrary) {
            item {
                DownloadBar(
                    workId = workId,
                    download = download,
                    onCancel = onCancelDownloads,
                    onRetry = onRetryDownloads,
                )
            }
        }
        item {
            Text(
                text = "Chapters (${state.chapters.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        items(state.chapters, key = { it.id }) { chapter ->
            ChapterRowItem(
                chapter = chapter,
                onClick = { onOpenChapter(chapter.id) },
                onOpenComments = { onOpenComments(chapter.id) },
                modifier = animateListItem(),
            )
        }
    }
}

@Composable
private fun StatsLine(work: Work) {
    val parts = buildList {
        work.wordCount?.let { add("${formatCount(it)} words") }
        work.chaptersPublished?.let { published ->
            add(if (work.chaptersTotal != null) "$published/${work.chaptersTotal} chapters" else "$published chapters")
        }
        work.kudos?.let { add("${formatCount(it.toLong())} kudos") }
        work.hits?.let { add("${formatCount(it)} hits") }
    }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
        )
    }
}

@Composable
private fun MetaBlock(work: Work) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        work.rating?.let { MetaLine("Rating", it) }
        if (work.warnings.isNotEmpty()) MetaLine("Warnings", work.warnings.joinToString(", "))
        if (work.fandoms.isNotEmpty()) MetaLine("Fandoms", work.fandoms.joinToString(", "))
        if (work.tags.isNotEmpty()) MetaLine("Tags", work.tags.joinToString(", "))
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsRow(
    inLibrary: Boolean,
    downloadBusy: Boolean,
    pendingCount: Int,
    downloadedCount: Int,
    nextCount: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadAll: () -> Unit,
    onDownloadUnread: () -> Unit,
    onDownloadNext: () -> Unit,
    onDeleteDownloads: () -> Unit,
) {
    // Wraps instead of clipping on a narrow screen: three labelled buttons do not fit everywhere.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (inLibrary) {
            OutlinedButton(onClick = onRemove) { Text("Remove") }
        } else {
            Button(onClick = onAdd) { Text("Add to library") }
        }
        OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        if (inLibrary) {
            DownloadMenu(
                enabled = !downloadBusy,
                pendingCount = pendingCount,
                downloadedCount = downloadedCount,
                nextCount = nextCount,
                onAll = onDownloadAll,
                onUnread = onDownloadUnread,
                onNext = onDownloadNext,
                onDelete = onDeleteDownloads,
            )
        }
    }
}

/** PRD 9.6 download menu: all, unread, next N, delete downloaded. */
@Composable
private fun DownloadMenu(
    enabled: Boolean,
    pendingCount: Int,
    downloadedCount: Int,
    nextCount: Int,
    onAll: () -> Unit,
    onUnread: () -> Unit,
    onNext: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled) {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Download")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("All chapters") },
                enabled = pendingCount > 0,
                onClick = {
                    open = false
                    onAll()
                },
            )
            DropdownMenuItem(
                text = { Text("Unread chapters") },
                enabled = pendingCount > 0,
                onClick = {
                    open = false
                    onUnread()
                },
            )
            DropdownMenuItem(
                text = { Text("Next $nextCount chapters") },
                enabled = pendingCount > 0,
                onClick = {
                    open = false
                    onNext()
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete downloaded") },
                enabled = downloadedCount > 0,
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun DownloadBar(
    workId: String,
    download: DownloadState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    if (download.workId != workId) return

    when {
        download.running -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Downloading ${download.processed} of ${download.total} chapters",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        if (download.total == 0) 0f else download.processed / download.total.toFloat()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }

        download.hasFailures -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${download.failed.size} chapters could not be downloaded",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun ChapterRowItem(
    chapter: ChapterRow,
    onClick: () -> Unit,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chapter.number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 5.dp),
        ) {
            Text(
                text = chapter.title.ifBlank { "Chapter ${chapter.number}" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (chapter.isRead) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            chapter.publishedAt?.let { date ->
                Text(
                    text = date.format(CHAPTER_DATE),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        if (chapter.isDownloaded) {
            Icon(
                imageVector = Icons.Outlined.DownloadDone,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp),
            )
        }
        if (chapter.isRead) {
            Text(
                text = "Read",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        IconButton(onClick = onOpenComments) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Comments",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** PRD 9.2: assign the work to any number of user categories. */
@Composable
private fun CategoryPickerDialog(
    categories: List<CategoryRow>,
    assigned: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            if (categories.isEmpty()) {
                Text("No categories yet. Create them in More → Categories.")
            } else {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    categories.forEach { row ->
                        val checked = row.category.id in assigned
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(row.category.id, !checked) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(10.dp))
                            Text(row.category.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}


private fun formatCount(value: Long): String = "%,d".format(value)
