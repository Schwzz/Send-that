package com.example.ui

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DownloadRepository
import com.example.intent.IntentParser
import com.example.model.DownloadProgress
import com.example.model.DownloadState
import com.example.model.DownloadedItem
import com.example.model.SharedInput
import com.example.network.DownloadStep
import com.example.network.MediaDownloader
import com.example.network.ResolutionResult
import com.example.network.UrlResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MediaShareViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = DownloadRepository(application, viewModelScope)
  private val urlResolver = UrlResolver()
  private val mediaDownloader = MediaDownloader()

  val downloadsList: StateFlow<List<DownloadedItem>> = repository.downloads

  private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
  val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

  private val _lastInputUrl = MutableStateFlow<String?>(null)
  val lastInputUrl: StateFlow<String?> = _lastInputUrl.asStateFlow()

  private var activeDownloadJob: Job? = null

  fun handleIncomingIntent(intent: Intent?, contentResolver: ContentResolver) {
    if (intent == null) return

    val parsed = IntentParser.parse(intent)
    when (parsed) {
      is SharedInput.Empty -> {
        // App launched normally
      }
      is SharedInput.WebUrl -> {
        startDownload(parsed.url, autoShare = true)
      }
      is SharedInput.MediaUris -> {
        importMediaUris(parsed, contentResolver)
      }
      is SharedInput.Error -> {
        _downloadState.value = DownloadState.Error(parsed.reason, sourceUrl = null)
      }
    }
  }

  fun startDownload(rawUrl: String, autoShare: Boolean = true) {
    val cleanUrl = rawUrl.trim()
    if (cleanUrl.isBlank()) return

    _lastInputUrl.value = cleanUrl
    activeDownloadJob?.cancel()

    activeDownloadJob = viewModelScope.launch {
      _downloadState.value = DownloadState.Resolving(cleanUrl)

      when (val resolution = urlResolver.resolve(cleanUrl)) {
        is ResolutionResult.DirectMedia -> {
          executeDownload(resolution.url, resolution.suggestedMime, null, autoShare)
        }
        is ResolutionResult.ResolvedWebMedia -> {
          executeDownload(resolution.directUrl, resolution.suggestedMime, resolution.title, autoShare)
        }
        is ResolutionResult.AuthRequiredOrBlocked -> {
          _downloadState.value = DownloadState.Error(
            reason = resolution.message,
            sourceUrl = cleanUrl,
            isHtmlError = true
          )
        }
        is ResolutionResult.NoMediaFound -> {
          _downloadState.value = DownloadState.Error(
            reason = resolution.message,
            sourceUrl = cleanUrl,
            isHtmlError = true
          )
        }
        is ResolutionResult.NetworkError -> {
          _downloadState.value = DownloadState.Error(
            reason = resolution.message,
            sourceUrl = cleanUrl,
            isHtmlError = false
          )
        }
      }
    }
  }

  private suspend fun executeDownload(
    mediaUrl: String,
    suggestedMime: String?,
    suggestedTitle: String?,
    autoShare: Boolean
  ) {
    _downloadState.value = DownloadState.Downloading(
      url = mediaUrl,
      progress = DownloadProgress(0L, -1L, null)
    )

    try {
      mediaDownloader.download(
        mediaUrl = mediaUrl,
        targetDirectory = repository.downloadsDir,
        suggestedMime = suggestedMime,
        suggestedFileName = suggestedTitle
      ).collect { step ->
        when (step) {
          is DownloadStep.Progress -> {
            _downloadState.value = DownloadState.Downloading(mediaUrl, step.progress)
          }
          is DownloadStep.Finished -> {
            repository.saveDownloadedItem(step.item)
            _downloadState.value = DownloadState.Success(step.item, autoShareTriggered = autoShare)
          }
        }
      }
    } catch (e: Exception) {
      val isHtml = e.message?.contains("HTML", ignoreCase = true) == true
      _downloadState.value = DownloadState.Error(
        reason = e.localizedMessage ?: "Failed to download media file",
        sourceUrl = mediaUrl,
        isHtmlError = isHtml
      )
    }
  }

  private fun importMediaUris(input: SharedInput.MediaUris, contentResolver: ContentResolver) {
    activeDownloadJob?.cancel()
    activeDownloadJob = viewModelScope.launch {
      _downloadState.value = DownloadState.Resolving("Importing shared media...")
      try {
        val firstUri = input.uris.first()
        val item = repository.importMediaUri(firstUri, contentResolver)
        _downloadState.value = DownloadState.Success(item, autoShareTriggered = true)
      } catch (e: Exception) {
        _downloadState.value = DownloadState.Error(
          reason = "Failed to copy shared media into private storage: ${e.localizedMessage}",
          sourceUrl = null
        )
      }
    }
  }

  fun cancelDownload() {
    activeDownloadJob?.cancel()
    activeDownloadJob = null
    _downloadState.value = DownloadState.Cancelled
  }

  fun retry() {
    val lastUrl = _lastInputUrl.value
    if (!lastUrl.isNullOrBlank()) {
      startDownload(lastUrl, autoShare = true)
    }
  }

  fun deleteDownload(item: DownloadedItem) {
    viewModelScope.launch {
      repository.deleteItem(item)
      // If currently showing this item in Success state, reset to Idle
      val current = _downloadState.value
      if (current is DownloadState.Success && current.item.id == item.id) {
        _downloadState.value = DownloadState.Idle
      }
    }
  }

  fun markAutoShareConsumed() {
    val current = _downloadState.value
    if (current is DownloadState.Success && current.autoShareTriggered) {
      _downloadState.value = current.copy(autoShareTriggered = false)
    }
  }

  fun clearState() {
    _downloadState.value = DownloadState.Idle
  }
}
