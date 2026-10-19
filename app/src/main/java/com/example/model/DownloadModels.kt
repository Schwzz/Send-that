package com.example.model

import android.net.Uri

data class DownloadedItem(
  val id: String,
  val fileName: String,
  val filePath: String,
  val sourceUrl: String?,
  val mimeType: String,
  val sizeBytes: Long,
  val downloadTimestamp: Long,
  val isVideo: Boolean,
  val isImage: Boolean
)

data class DownloadProgress(
  val bytesDownloaded: Long,
  val totalBytes: Long,
  val percentage: Float?
)

sealed interface DownloadState {
  object Idle : DownloadState
  data class Resolving(val url: String) : DownloadState
  data class Downloading(val url: String, val progress: DownloadProgress) : DownloadState
  data class Success(val item: DownloadedItem, val autoShareTriggered: Boolean) : DownloadState
  data class Error(val reason: String, val sourceUrl: String?, val isHtmlError: Boolean = false) : DownloadState
  object Cancelled : DownloadState
}

sealed interface SharedInput {
  object Empty : SharedInput
  data class WebUrl(val url: String, val rawText: String?) : SharedInput
  data class MediaUris(val uris: List<Uri>, val mimeType: String) : SharedInput
  data class Error(val reason: String) : SharedInput
}
