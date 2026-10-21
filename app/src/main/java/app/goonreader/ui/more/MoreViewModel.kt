package app.goonreader.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.goonreader.data.prefs.AppPreferences
import app.goonreader.data.prefs.AppSettings
import app.goonreader.data.prefs.ReaderPreferences
import app.goonreader.data.prefs.ReaderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val readerPreferences: ReaderPreferences,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val readerSettings: StateFlow<ReaderSettings> = readerPreferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    fun setDownloadedOnly(value: Boolean) = viewModelScope.launch {
        preferences.update { it.copy(downloadedOnly = value) }
    }

    fun setIncognito(value: Boolean) = viewModelScope.launch {
        preferences.update { it.copy(incognito = value) }
    }

    fun updateReader(transform: (ReaderSettings) -> ReaderSettings) = viewModelScope.launch {
        readerPreferences.update(transform)
    }
}
