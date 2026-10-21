package app.goonreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.goonreader.data.repo.FailureReason

/**
 * One failure screen for every screen that fetches AO3, so the wording and the retry button
 * never drift apart. [secondaryAction] is for the extra escape hatch (open in browser).
 */
@Composable
fun FailureState(
    reason: FailureReason,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = failureTitle(reason),
    message: String = failureMessage(reason),
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text("Try again") }
            secondaryAction?.let { it() }
        }
    }
}

fun failureTitle(reason: FailureReason): String = when (reason) {
    FailureReason.NotFound -> "Not found"
    FailureReason.ChallengeRequired -> "AO3 wants a browser check"
    FailureReason.Offline -> "No connection"
    FailureReason.HttpError -> "AO3 returned an error"
    FailureReason.ParseError -> "Couldn't read that page"
}

fun failureMessage(reason: FailureReason): String = when (reason) {
    FailureReason.NotFound -> "AO3 has nothing at this address. It may have been deleted or made private."
    FailureReason.ChallengeRequired -> "Open AO3 in your browser to clear the check, then try again."
    FailureReason.Offline -> "Check your connection and try again."
    FailureReason.HttpError -> "The archive is busy or unavailable. Try again in a moment."
    FailureReason.ParseError -> "AO3 may have changed its layout. Try again, or use your browser."
}
