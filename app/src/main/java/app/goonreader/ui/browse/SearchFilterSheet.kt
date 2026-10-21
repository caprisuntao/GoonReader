package app.goonreader.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.goonreader.domain.model.AO3_LANGUAGES
import app.goonreader.domain.model.Category
import app.goonreader.domain.model.Completion
import app.goonreader.domain.model.Crossovers
import app.goonreader.domain.model.Language
import app.goonreader.domain.model.Rating
import app.goonreader.domain.model.SearchFilters
import app.goonreader.domain.model.SortColumn
import app.goonreader.domain.model.SortDirection
import app.goonreader.domain.model.UpdatedWithin
import app.goonreader.domain.model.Warning

/**
 * The filter sheet, mapped one-to-one onto AO3's `work_search[...]` parameters.
 *
 * Everything edits a local draft; only "Show results" commits, so a half-typed fandom never
 * triggers a request. Enum values (ratings, warnings, categories) are the ids AO3's own form
 * uses — see `SearchFilters`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterSheet(
    filters: SearchFilters,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(filters) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { draft = draft.cleared() }) { Text("Clear") }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
            ) {
                item {
                    SectionTitle("Search")
                    FilterField("Any field", draft.query) { draft = draft.copy(query = it) }
                    FilterField("Title", draft.title) { draft = draft.copy(title = it) }
                    FilterField("Creator", draft.creators) { draft = draft.copy(creators = it) }
                }
                item {
                    SectionTitle("Tags")
                    FilterField("Fandoms", draft.fandomNames, "Harry Potter - J. K. Rowling") {
                        draft = draft.copy(fandomNames = it)
                    }
                    FilterField("Relationships", draft.relationshipNames, "A/B, C/D") {
                        draft = draft.copy(relationshipNames = it)
                    }
                    FilterField("Characters", draft.characterNames, "A, B") {
                        draft = draft.copy(characterNames = it)
                    }
                    FilterField("Additional tags", draft.freeformNames, "Time Travel") {
                        draft = draft.copy(freeformNames = it)
                    }
                }
                item {
                    SectionTitle("Rating")
                    ChipRow(
                        options = listOf<Pair<String, Rating?>>("Any" to null) + Rating.entries.map { it.label to it },
                        selected = draft.rating,
                        onSelect = { draft = draft.copy(rating = it) },
                    )
                }
                item {
                    SectionTitle("Warnings")
                    MultiChipRow(
                        options = Warning.entries.map { it.label to it },
                        selected = draft.warnings,
                        onToggle = { warning, on ->
                            draft = draft.copy(
                                warnings = if (on) draft.warnings + warning else draft.warnings - warning,
                            )
                        },
                    )
                }
                item {
                    SectionTitle("Categories")
                    MultiChipRow(
                        options = Category.entries.map { it.label to it },
                        selected = draft.categories,
                        onToggle = { category, on ->
                            draft = draft.copy(
                                categories = if (on) draft.categories + category else draft.categories - category,
                            )
                        },
                    )
                }
                item {
                    SectionTitle("Completion")
                    ChipRow(
                        options = Completion.entries.map { it.label to it },
                        selected = draft.completion,
                        onSelect = { draft = draft.copy(completion = it) },
                    )
                }
                item {
                    SectionTitle("Crossovers")
                    ChipRow(
                        options = Crossovers.entries.map { it.label to it },
                        selected = draft.crossovers,
                        onSelect = { draft = draft.copy(crossovers = it) },
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Single chapter only",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = draft.singleChapterOnly,
                            onCheckedChange = { draft = draft.copy(singleChapterOnly = it) },
                        )
                    }
                }
                item {
                    SectionTitle("Word count")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            label = "From",
                            value = draft.wordsFrom,
                            modifier = Modifier.weight(1f),
                            onValueChange = { draft = draft.copy(wordsFrom = it) },
                        )
                        NumberField(
                            label = "To",
                            value = draft.wordsTo,
                            modifier = Modifier.weight(1f),
                            onValueChange = { draft = draft.copy(wordsTo = it) },
                        )
                    }
                }
                item {
                    SectionTitle("Language")
                    ChoiceField(
                        label = "Language",
                        value = AO3_LANGUAGES.firstOrNull { it.id == draft.languageId }?.label ?: "Any language",
                        options = listOf<Pair<String, Language?>>("Any language" to null) +
                            AO3_LANGUAGES.map { it.label to it },
                        selected = AO3_LANGUAGES.firstOrNull { it.id == draft.languageId },
                        onSelect = { language -> draft = draft.copy(languageId = language?.id) },
                    )
                }
                item {
                    SectionTitle("Updated")
                    ChipRow(
                        options = UpdatedWithin.entries.map { it.label to it },
                        selected = draft.updatedWithin,
                        onSelect = { draft = draft.copy(updatedWithin = it) },
                    )
                }
                item {
                    SectionTitle("Sort")
                    ChoiceField(
                        label = "Sort by",
                        value = draft.sortColumn.label,
                        options = SortColumn.entries.map { it.label to it },
                        selected = draft.sortColumn,
                        onSelect = { draft = draft.copy(sortColumn = it) },
                    )
                    ChipRow(
                        options = SortDirection.entries.map { it.label to it },
                        selected = draft.sortDirection,
                        onSelect = { draft = draft.copy(sortDirection = it) },
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            HorizontalDivider()
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    when (val count = draft.filterCount) {
                        0 -> "Show results"
                        1 -> "Show results (1 filter)"
                        else -> "Show results ($count filters)"
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun FilterField(
    label: String,
    value: String,
    placeholder: String? = null,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

@Composable
private fun NumberField(
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
    onValueChange: (Int?) -> Unit,
) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.toIntOrNull()) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Single-select chips; [selected] is compared by value, so `null` means "Any". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MultiChipRow(
    options: List<Pair<String, T>>,
    selected: Set<T>,
    onToggle: (T, Boolean) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            val checked = value in selected
            FilterChip(
                selected = checked,
                onClick = { onToggle(value, !checked) },
                label = { Text(label) },
            )
        }
    }
}

/** A read-only row that opens a list of options. Cheaper to get right than a dropdown menu. */
@Composable
private fun <T> ChoiceField(
    label: String,
    value: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { open = true }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.Outlined.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(options.size) { index ->
                        val (optionLabel, optionValue) = options[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(optionValue)
                                    open = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = optionValue == selected, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(optionLabel, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Close") }
            },
        )
    }
}
