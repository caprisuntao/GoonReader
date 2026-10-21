package app.goonreader.ui.storage

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.goonreader.data.backup.BackupRepository
import app.goonreader.data.backup.ImportResult
import app.goonreader.data.cache.ChapterCache
import app.goonreader.data.download.DownloadManager
import app.goonreader.data.download.DownloadStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StorageUiState(
    val databaseBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val downloadBytes: Long = 0L,
)

/** Data and storage (PRD 9.8): what the three layers cost, the clear buttons, and backup. */
@HiltViewModel
class StorageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: ChapterCache,
    private val store: DownloadStore,
    private val downloads: DownloadManager,
    private val backup: BackupRepository,
) : ViewModel() {

    private val refreshCounter = MutableStateFlow(0)

    val state: StateFlow<StorageUiState> = refreshCounter
        .map { measure() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refresh() {
        refreshCounter.value++
    }

    fun clearCache() = viewModelScope.launch {
        cache.clear()
        refresh()
    }

    fun clearDownloads() = viewModelScope.launch {
        downloads.deleteAllDownloads()
        refresh()
    }

    /** PRD 9.8: the library as JSON, written wherever the system picker points. */
    fun export(uri: Uri) = viewModelScope.launch {
        _message.value = null
        runCatching {
            val json = backup.export()
            withContext(Dispatchers.IO) {
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not write that file.")
                stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
        }.onSuccess {
            _message.value = "Backup exported."
        }.onFailure { failure ->
            _message.value = "Export failed: ${failure.message ?: "unknown error"}"
        }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        _message.value = null
        runCatching {
            val text = withContext(Dispatchers.IO) {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: error("Could not read that file.")
                stream.use { it.readBytes().toString(Charsets.UTF_8) }
            }
            when (val result = backup.import(text)) {
                is ImportResult.Imported -> {
                    refresh()
                    _message.value = "Imported ${result.works} works and ${result.chapters} chapters."
                }
                ImportResult.InvalidFile -> _message.value = "That file is not a GoonReader backup."
            }
        }.onFailure { failure ->
            _message.value = "Import failed: ${failure.message ?: "unknown error"}"
        }
    }

    private fun measure() = StorageUiState(
        databaseBytes = DATABASE_FILES.sumOf { context.getDatabasePath(it).length() },
        cacheBytes = cache.sizeBytes(),
        downloadBytes = store.sizeBytes(),
    )

    private companion object {
        /** WAL mode keeps the write-ahead log next to the database; count it too. */
        val DATABASE_FILES = listOf("goonreader.db", "goonreader.db-wal", "goonreader.db-shm")
    }
}
