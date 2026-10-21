package app.goonreader.ui.comments

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.data.network.Ao3Urls
import app.goonreader.data.repo.FailureReason
import app.goonreader.domain.model.Comment
import app.goonreader.ui.components.BackButton
import app.goonreader.ui.components.EmptyState
import app.goonreader.ui.components.FailureState
import app.goonreader.ui.components.ScreenScaffold
import app.goonreader.ui.components.failureMessage
import app.goonreader.ui.theme.GoonReaderTheme
import app.goonreader.ui.theme.animateListItem
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val COMMENT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** Read-only, threaded, paginated chapter comments (PRD 9.10). */
@Composable
fun CommentsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ScreenScaffold(
        title = "Comments",
        modifier = modifier,
        navigationIcon = { BackButton(onBack) },
        actions = {
            // PRD 9.10: anything beyond reading happens in the browser.
            IconButton(
                onClick = {
                    val url = if (viewModel.chapterId == viewModel.workId) {
                        Ao3Urls.work(viewModel.workId)
                    } else {
                        Ao3Urls.chapter(viewModel.workId, viewModel.chapterId)
                    }
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = "Open in browser")
            }
        },
    ) {
        when {
            state.loading -> CommentSkeleton()

            state.failure != null && state.comments.isEmpty() -> FailureState(
                reason = state.failure ?: FailureReason.HttpError,
                onRetry = viewModel::retry,
                message = if (state.failure == FailureReason.Offline) {
                    "Comments need a connection, even for downloaded chapters."
                } else {
                    failureMessage(state.failure ?: FailureReason.HttpError)
                },
            )

            state.comments.isEmpty() -> EmptyState(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "No comments yet",
                message = "This chapter has no comments, or the author restricted them.",
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header") {
                    Text(
                        text = state.total?.let { if (it == 1) "1 comment" else "$it comments" }
                            ?: "${state.comments.size} comments",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                items(state.comments, key = { it.id }) { comment ->
                    CommentItem(comment, animateListItem())
                }
                item(key = "footer") {
                    CommentFooter(state = state, onLoadMore = viewModel::loadMore, onRetry = viewModel::retry)
                }
            }
        }
    }
}

@Composable
private fun CommentItem(comment: Comment, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // PRD 9.10: cap the visual indentation at three levels, then flatten.
            .padding(
                start = 20.dp + (minOf(comment.depth, 3) * 16).dp,
                end = 20.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = comment.author,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            comment.postedAt?.let { date -> Text(dateLabel(date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = commentPlainText(comment.bodyHtml),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    HorizontalDivider(color = GoonReaderTheme.colors.divider.copy(alpha = 0.3f))
}

@Composable
private fun CommentFooter(state: CommentsUiState, onLoadMore: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        val failure = state.failure
        when {
            state.loadingMore -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.width(24.dp).height(24.dp))
            }

            failure != null -> {
                Text(
                    text = "Couldn't load more comments.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }

            state.hasMore -> TextButton(
                onClick = onLoadMore,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Load more") }
        }
    }
}

/** Skeleton rows while the first page is fetched (PRD 9.10 asks for one). */
@Composable
private fun CommentSkeleton() {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        repeat(5) {
            SkeletonBar(widthFraction = 0.35f, height = 12.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonBar(widthFraction = 0.95f, height = 10.dp)
            Spacer(Modifier.height(4.dp))
            SkeletonBar(widthFraction = 0.8f, height = 10.dp)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)),
    )
}

private fun dateLabel(date: LocalDate): String = date.format(COMMENT_DATE)

/**
 * The list shows text, not HTML. AO3 comments are mostly paragraphs and italics; flattening
 * keeps the screen safe without a WebView per row, and paragraph breaks survive.
 */
internal fun commentPlainText(html: String): String {
    val body = Jsoup.parseBodyFragment(html).body()
    val paragraphs = body.select("p").map { it.text().trim() }.filter { it.isNotEmpty() }
    return if (paragraphs.isNotEmpty()) paragraphs.joinToString("\n\n") else body.text().trim()
}
