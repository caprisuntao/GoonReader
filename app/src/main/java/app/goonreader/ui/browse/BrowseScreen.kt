package app.goonreader.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import app.goonreader.data.paging.SearchLoadException
import app.goonreader.data.repo.FailureReason
import app.goonreader.domain.model.TagRef
import app.goonreader.domain.model.TagType
import app.goonreader.domain.model.Work
import app.goonreader.ui.components.EmptyState
import app.goonreader.ui.components.FailureState
import app.goonreader.ui.components.ScreenScaffold
import app.goonreader.ui.theme.GoonReaderTheme
import app.goonreader.ui.theme.Motion
import app.goonreader.ui.theme.animateListItem
import org.jsoup.Jsoup
import java.time.format.DateTimeFormatter
import java.util.Locale

private val UPDATED_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** 0 for the entry pane, 1 for results and the tag picker: drives the slide direction. */
private val BrowsePage.depth: Int get() = if (this is BrowsePage.Home) 0 else 1

/**
 * Browse: one search bar over three panes — quick entry points, results, and the tag picker.
 * Previewing a result only opens Work details, which never writes unless the user adds it.
 */
@Composable
fun BrowseScreen(
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraryIds by viewModel.libraryIds.collectAsStateWithLifecycle()
    val downloadedOnly by viewModel.downloadedOnly.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = state.page !is BrowsePage.Home) { viewModel.back() }

    ScreenScaffold(title = "Browse", modifier = modifier) {
        if (downloadedOnly) {
            EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = "Downloaded only is on",
                message = "Browse needs a connection. Turn Downloaded only off in More to search AO3; " +
                    "your downloaded works are in the Library.",
            )
        } else {
            BrowsePanes(
                state = state,
                viewModel = viewModel,
                libraryIds = libraryIds,
                onOpenWork = onOpenWork,
                onOpenFilters = { showFilters = true },
            )
        }
    }

    if (showFilters) {
        SearchFilterSheet(
            // The bar's text may be edited but not submitted yet; opening the sheet must not
            // drop it when the filters are applied.
            filters = state.filters.copy(query = state.query),
            onDismiss = { showFilters = false },
            onApply = { filters ->
                showFilters = false
                viewModel.applyFilters(filters)
            },
        )
    }
}

/** The three Browse panes, split out so the Downloaded only notice can replace them in one branch. */
@Composable
private fun BrowsePanes(
    state: BrowseUiState,
    viewModel: BrowseViewModel,
    libraryIds: Set<String>,
    onOpenWork: (String) -> Unit,
    onOpenFilters: () -> Unit,
) {
    AnimatedContent(
        targetState = state.page,
        // A new query is still the Results pane; only a change of pane should animate.
        contentKey = { it::class },
        transitionSpec = {
            val forward = targetState.depth >= initialState.depth
            val direction = if (forward) 1 else -1
            (slideInHorizontally(tween(Motion.Standard, easing = Motion.EaseOut)) {
                direction * it / 10
            } + fadeIn(tween(Motion.Standard, easing = Motion.EaseOut)))
                .togetherWith(
                    slideOutHorizontally(tween(Motion.Fast, easing = Motion.EaseIn)) {
                        -direction * it / 12
                    } + fadeOut(tween(Motion.Fast, easing = Motion.EaseIn)),
                )
        },
        modifier = Modifier.fillMaxSize(),
        label = "browsePane",
    ) { page ->
        Column(Modifier.fillMaxSize()) {
            when (page) {
                BrowsePage.Home -> {
                    WorkSearchBar(
                        query = state.query,
                        onQueryChange = viewModel::onQueryChange,
                        onSubmit = viewModel::submitQuery,
                        onOpenFilters = onOpenFilters,
                        filterCount = state.filters.filterCount,
                        onClose = null,
                    )
                    HomeEntries(
                        onLatest = viewModel::openLatest,
                        onMostKudos = viewModel::openMostKudos,
                        onTags = viewModel::openTagPicker,
                    )
                }

                is BrowsePage.Results -> {
                    WorkSearchBar(
                        query = state.query,
                        onQueryChange = viewModel::onQueryChange,
                        onSubmit = viewModel::submitQuery,
                        onOpenFilters = onOpenFilters,
                        filterCount = state.filters.filterCount,
                        onClose = viewModel::back,
                    )
                    // A new search must start at the top: without this key the list keeps the
                    // scroll offset (and the loaded pages) of whatever was searched before.
                    key(state.searchId) {
                        ResultsPane(
                            viewModel = viewModel,
                            libraryIds = libraryIds,
                            onOpenWork = onOpenWork,
                            title = page.title,
                        )
                    }
                }

                BrowsePage.Tags -> TagSearchPane(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun WorkSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenFilters: () -> Unit,
    filterCount: Int,
    onClose: (() -> Unit)?,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search AO3") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Back to browse")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    onSubmit()
                },
            ),
        )
        BadgedBox(
            badge = {
                if (filterCount > 0) Badge { Text(filterCount.toString()) }
            },
        ) {
            IconButton(onClick = onOpenFilters) {
                Icon(Icons.Outlined.FilterList, contentDescription = "Filters")
            }
        }
    }
}

@Composable
private fun HomeEntries(onLatest: () -> Unit, onMostKudos: () -> Unit, onTags: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        QuickEntry(
            icon = Icons.Outlined.Schedule,
            title = "Latest",
            subtitle = "Works updated most recently",
            onClick = onLatest,
        )
        HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
        QuickEntry(
            icon = Icons.Outlined.FavoriteBorder,
            title = "Most kudos",
            subtitle = "The most-kudosed works on AO3",
            onClick = onMostKudos,
        )
        HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
        QuickEntry(
            icon = Icons.Outlined.Tag,
            title = "Browse by tag",
            subtitle = "Find a fandom, character, relationship or tag",
            onClick = onTags,
        )
    }
}

@Composable
private fun QuickEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
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
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultsPane(
    viewModel: BrowseViewModel,
    libraryIds: Set<String>,
    onOpenWork: (String) -> Unit,
    title: String,
) {
    val items = viewModel.results.collectAsLazyPagingItems()
    val refresh = items.loadState.refresh

    when {
        refresh is LoadState.Loading && items.itemCount == 0 -> CenteredProgress()

        refresh is LoadState.Error && items.itemCount == 0 -> FailureState(
            reason = refresh.error.failureReason(),
            onRetry = { items.retry() },
        )

        refresh is LoadState.NotLoading && items.itemCount == 0 -> EmptyState(
            icon = Icons.Outlined.Explore,
            title = "No works matched",
            message = "$title returned nothing. Try fewer filters or different words.",
        )

        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(
                count = items.itemCount,
                key = { index -> items.peek(index)?.id ?: "row-$index" },
            ) { index ->
                val work = items[index]
                if (work != null) {
                    WorkListItem(
                        work = work,
                        inLibrary = work.id in libraryIds,
                        onClick = { onOpenWork(work.id) },
                        modifier = animateListItem(),
                    )
                }
            }
            item(key = "footer") {
                // Appending the next page is where a rate limit or a dropped connection shows up.
                when (val append = items.loadState.append) {
                    is LoadState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                    }

                    is LoadState.Error -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = append.error.failureReason().let { "Couldn't load more: ${it.label()}" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { items.retry() }) { Text("Retry") }
                    }

                    else -> Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkListItem(
    work: Work,
    inLibrary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = remember(work.id) { work.summaryHtml?.let { Jsoup.parse(it).text().trim() }.orEmpty() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = work.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (inLibrary) {
                Spacer(Modifier.width(8.dp))
                Pill(text = "In library", tint = MaterialTheme.colorScheme.primary)
            }
        }
        val byline = byline(work)
        if (byline.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = byline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            work.rating?.let { Pill(it) }
            Pill(if (work.isComplete) "Complete" else "WIP")
            work.language?.takeIf { it != "English" }?.let { Pill(it) }
        }
        if (summary.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = statsLine(work),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
    }
    HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
}

@Composable
private fun Pill(
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSearchPane(state: BrowseUiState, viewModel: BrowseViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.tagQuery,
            onValueChange = viewModel::onTagQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            placeholder = { Text("Tag name") },
            leadingIcon = { Icon(Icons.Outlined.Tag, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    viewModel.submitTagSearch()
                },
            ),
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TagType.entries
                .filter { it != TagType.Unsorted && it != TagType.Unknown }
                .forEach { type ->
                    FilterChip(
                        selected = state.tagType == type,
                        onClick = { viewModel.onTagTypeChange(type) },
                        label = { Text(type.label) },
                    )
                }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.tagResults == null) "Type a name, then search." else "Tap a tag to browse its works.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    keyboard?.hide()
                    viewModel.submitTagSearch()
                },
                enabled = state.tagQuery.isNotBlank() && !state.tagLoading,
            ) { Text("Search") }
        }
        HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))

        val failure = state.tagFailure
        val results = state.tagResults
        when {
            state.tagLoading -> CenteredProgress()
            failure != null -> FailureState(reason = failure, onRetry = viewModel::submitTagSearch)
            results == null -> EmptyState(
                icon = Icons.Outlined.Tag,
                title = "Browse by tag",
                message = "Search for a fandom, character, relationship or freeform tag. AO3's own tag pages open from the results.",
            )
            results.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Tag,
                title = "No tags matched",
                message = "Try a shorter name, or another tag type.",
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.href }) { tag ->
                    TagListItem(
                        tag = tag,
                        onClick = { viewModel.openTag(tag) },
                        modifier = animateListItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagListItem(tag: TagRef, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildString {
                append(tag.type.label)
                tag.workCount?.let { works ->
                    append(" · ").append(compactCount(works.toLong()))
                    append(if (works == 1) " work" else " works")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.35f))
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private fun byline(work: Work): String = listOfNotNull(
    work.authors.joinToString(", ").takeIf { it.isNotBlank() },
    work.fandoms.firstOrNull()?.takeIf { it.isNotBlank() },
).joinToString(" · ")

private fun statsLine(work: Work): String = buildList {
    work.wordCount?.let { add("${compactCount(it)} words") }
    work.chaptersPublished?.let { published ->
        add(work.chaptersTotal?.let { total -> "$published/$total ch" } ?: "$published ch")
    }
    work.kudos?.let { add("${compactCount(it.toLong())} kudos") }
    work.updatedAt?.let { add("updated ${it.format(UPDATED_DATE)}") }
}.joinToString(" · ")

/** 86,446 -> "86k". Long counts are decoration, not precision. */
private fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(Locale.US, value / 1_000_000.0)
    value >= 100_000 -> "${value / 1_000}k"
    value >= 1_000 -> "%.1fk".format(Locale.US, value / 1_000.0)
    else -> value.toString()
}

/** Paging hands the error back as a throwable; the reason is ours. */
internal fun Throwable?.failureReason(): FailureReason =
    (this as? SearchLoadException)?.reason ?: FailureReason.HttpError

internal fun FailureReason.label(): String = when (this) {
    FailureReason.NotFound -> "gone"
    FailureReason.ChallengeRequired -> "browser check"
    FailureReason.Offline -> "no connection"
    FailureReason.HttpError -> "AO3 error"
    FailureReason.ParseError -> "unreadable page"
}
