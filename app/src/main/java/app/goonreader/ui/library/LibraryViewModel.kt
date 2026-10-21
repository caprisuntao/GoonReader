package app.goonreader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.goonreader.data.db.CategoryRow
import app.goonreader.data.db.LibraryRow
import app.goonreader.data.prefs.AppPreferences
import app.goonreader.data.repo.CategoryRepository
import app.goonreader.data.repo.WorkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LibraryUiState(
    val rows: List<LibraryRow> = emptyList(),
    val categories: List<CategoryRow> = emptyList(),
    val selectedCategoryId: Long? = null,
    val downloadedOnly: Boolean = false,
)

/** Library list with the category tabs from PRD 9.2 and the Downloaded only filter from 9.7. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: WorkRepository,
    categories: CategoryRepository,
    preferences: AppPreferences,
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<Long?>(null)

    private val filter = combine(
        selectedCategory,
        preferences.settings.map { it.downloadedOnly },
    ) { categoryId, downloadedOnly -> categoryId to downloadedOnly }

    val state: StateFlow<LibraryUiState> = filter
        .flatMapLatest { (categoryId, downloadedOnly) ->
            combine(
                repository.observeLibrary(categoryId, downloadedOnly),
                categories.observeAll(),
            ) { rows, allCategories ->
                LibraryUiState(
                    rows = rows,
                    categories = allCategories,
                    selectedCategoryId = categoryId,
                    downloadedOnly = downloadedOnly,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun selectCategory(categoryId: Long?) {
        selectedCategory.value = categoryId
    }
}
