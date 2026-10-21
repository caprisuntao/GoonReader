package app.goonreader.ui.downloads

import androidx.lifecycle.ViewModel
import app.goonreader.data.download.DownloadManager
import app.goonreader.data.download.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Thin view over the in-process queue, so More has a place to see and stop it (PRD 9.9). */
@HiltViewModel
class DownloadQueueViewModel @Inject constructor(private val downloads: DownloadManager) : ViewModel() {

    val state: StateFlow<DownloadState> = downloads.state

    fun cancel() = downloads.cancel()

    fun retry() = downloads.retryFailed()
}
