package app.goonreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goonreader.data.prefs.ReaderAction
import app.goonreader.data.prefs.ReaderMode
import app.goonreader.data.prefs.ReaderSettings
import app.goonreader.data.prefs.ReaderTheme
import app.goonreader.data.prefs.PageAnimation
import app.goonreader.ui.theme.animateListItem
import kotlin.math.roundToInt

/** Live appearance panel: every change applies immediately and for later chapters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    /** Passed only by the reader, which is the screen that can start auto-scroll. */
    autoScroll: Boolean = false,
    onAutoScrollChange: ((Boolean) -> Unit)? = null,
    /** Also only from the reader: importing a font needs a document picker. */
    customFont: Boolean = false,
    fontError: String? = null,
    onPickFont: (() -> Unit)? = null,
    onClearFont: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Theme", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { onChange { it.copy(theme = theme) } },
                        label = { Text(theme.label) },
                    )
                }
            }

            if (settings.theme == ReaderTheme.Custom) {
                SwatchRow(
                    label = "Background",
                    colors = BACKGROUND_SWATCHES,
                    selected = settings.customBackground,
                    onSelect = { color -> onChange { it.copy(customBackground = color) } },
                )
                SwatchRow(
                    label = "Text",
                    colors = TEXT_SWATCHES,
                    selected = settings.customText,
                    onSelect = { color -> onChange { it.copy(customText = color) } },
                )
            }

            Text("Reading mode", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.mode == mode,
                        onClick = { onChange { it.copy(mode = mode) } },
                        label = { Text(mode.label) },
                    )
                }
            }

            if (settings.mode == ReaderMode.Paged) {
                Text("Page turn", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageAnimation.entries.forEach { animation ->
                        FilterChip(
                            selected = settings.pageAnimation == animation,
                            onClick = { onChange { it.copy(pageAnimation = animation) } },
                            label = { Text(animation.label) },
                        )
                    }
                }
            }

            SliderRow("Font size", "${settings.fontSizeSp.roundToInt()} sp") { modifier ->
                Slider(
                    value = settings.fontSizeSp,
                    onValueChange = { value -> onChange { it.copy(fontSizeSp = value) } },
                    valueRange = 12f..36f,
                    modifier = modifier,
                )
            }
            SliderRow("Line spacing", "%.2f".format(settings.lineSpacing)) { modifier ->
                Slider(
                    value = settings.lineSpacing,
                    onValueChange = { value -> onChange { it.copy(lineSpacing = value) } },
                    valueRange = 1f..2.5f,
                    modifier = modifier,
                )
            }
            SliderRow("Paragraph spacing", "%.1f em".format(settings.paragraphSpacingEm)) { modifier ->
                Slider(
                    value = settings.paragraphSpacingEm,
                    onValueChange = { value -> onChange { it.copy(paragraphSpacingEm = value) } },
                    valueRange = 0f..2f,
                    modifier = modifier,
                )
            }
            SliderRow("Side margins", "${settings.sideMarginDp} dp") { modifier ->
                Slider(
                    value = settings.sideMarginDp.toFloat(),
                    onValueChange = { value -> onChange { it.copy(sideMarginDp = value.roundToInt()) } },
                    valueRange = 0f..64f,
                    modifier = modifier,
                )
            }
            SliderRow("Top/bottom margins", "${settings.verticalMarginDp} dp") { modifier ->
                Slider(
                    value = settings.verticalMarginDp.toFloat(),
                    onValueChange = { value -> onChange { it.copy(verticalMarginDp = value.roundToInt()) } },
                    valueRange = 0f..64f,
                    modifier = modifier,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SwitchRow("Justify text", settings.justify) { value -> onChange { it.copy(justify = value) } }
            SwitchRow("Show chapter summary", settings.showSummary) { value ->
                onChange { it.copy(showSummary = value) }
            }
            SwitchRow("Show author notes", settings.showNotes) { value -> onChange { it.copy(showNotes = value) } }
            SwitchRow("Keep screen on", settings.keepScreenOn) { value -> onChange { it.copy(keepScreenOn = value) } }
            SwitchRow("Volume keys turn pages", settings.volumeKeys) { value ->
                onChange { it.copy(volumeKeys = value) }
            }
            SwitchRow("Status overlay", settings.statusOverlay) { value ->
                onChange { it.copy(statusOverlay = value) }
            }
            if (onAutoScrollChange != null) {
                SwitchRow("Auto-scroll", autoScroll) { onAutoScrollChange(it) }
                if (autoScroll) {
                    SliderRow("Auto-scroll speed", "${settings.autoScrollSpeed} dp/s") { modifier ->
                        Slider(
                            value = settings.autoScrollSpeed.toFloat(),
                            onValueChange = { value -> onChange { it.copy(autoScrollSpeed = value.roundToInt()) } },
                            valueRange = 10f..300f,
                            modifier = modifier,
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (onPickFont != null) {
                Text("Font", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (customFont) "Imported font" else "System font",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onPickFont) {
                        Text(if (customFont) "Replace…" else "Import…")
                    }
                }
                if (customFont && onClearFont != null) {
                    TextButton(onClick = onClearFont) { Text("Use the system font") }
                }
                fontError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

            var showZones by remember { mutableStateOf(false) }
            TextButton(onClick = { showZones = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Tap zones…", modifier = Modifier.fillMaxWidth())
            }
            if (showZones) {
                TapZoneDialog(
                    settings = settings,
                    onChange = onChange,
                    onDismiss = { showZones = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchRow(
    label: String,
    colors: List<Long>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.forEach { value ->
            val color = value.toInt()
            // 48dp cell around a 30dp swatch, so the target stays a finger wide.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(
                            width = if (color == selected) 3.dp else 1.dp,
                            color = if (color == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

private val BACKGROUND_SWATCHES = listOf(
    0xFF1B1612, 0xFF000000, 0xFF2A2118, 0xFF3A2E22,
    0xFFF7F1E6, 0xFFF1E6CF, 0xFFE7EDE4, 0xFFE6EEF5,
    0xFFF6E7E7, 0xFF14181C,
)

private val TEXT_SWATCHES = listOf(
    0xFFE6DCCF, 0xFFCFC8BD, 0xFFF2E9DC, 0xFFB9835A,
    0xFF2B2622, 0xFF4A3A28, 0xFF17202A, 0xFF5B1F1F,
    0xFF1F3B2C, 0xFF0B0B0B,
)

@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    slider: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    slider(Modifier.fillMaxWidth())
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * PRD 10.2 asks for a remappable 3x3 grid with a preview. The grid itself is the preview: each
 * cell shows what it will do, and tapping it swaps in another action.
 */
@Composable
private fun TapZoneDialog(
    settings: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Tap zones" else "Zone ${(editing ?: 0) + 1}") },
        text = {
            if (editing == null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0 until 3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (column in 0 until 3) {
                                val index = row * 3 + column
                                Surface(
                                    onClick = { editing = index },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.width(88.dp),
                                ) {
                                    Text(
                                        text = settings.tapZones[index].label,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 14.dp),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    ReaderAction.entries.forEach { action ->
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val index = editing ?: return@clickable
                                    onChange { it.withTapZone(index, action) }
                                    editing = null
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (editing == null) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(onClick = { editing = null }) { Text("Back") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListSheet(
    chapters: List<ReaderChapter>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 520.dp)) {
            itemsIndexed(chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .then(animateListItem()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chapter.number.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp),
                    )
                    Text(
                        text = chapter.title.ifBlank { "Chapter ${chapter.number}" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (index == currentIndex) {
                            MaterialTheme.colorScheme.primary
                        } else if (chapter.isRead) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (chapter.isRead) {
                        Text(
                            text = "Read",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
