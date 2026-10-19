package com.example.network

import com.example.model.DownloadedItem
import com.example.model.DownloadProgress
import com.example.util.MediaDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface DownloadStep {
  data class Progress(val progress: DownloadProgress) : DownloadStep
  data class Finished(val item: DownloadedItem) : DownloadStep
}

class MediaDownloader(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .followRedirects(true)
    .build(),
  private val maxFileSizeBytes: Long = 150 * 1024 * 1024L // 150 MB
) {

  fun download(
    mediaUrl: String,
    targetDirectory: File,
    suggestedMime: String? = null,
    suggestedFileName: String? = null
  ): Flow<DownloadStep> = flow {
    targetDirectory.mkdirs()

    val tempFile = File(targetDirectory, "download_${UUID.randomUUID()}.part")

    val request = Request.Builder()
      .url(mediaUrl)
      .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
      .header("Accept", "*/*")
      .build()

    try {
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          throw IOException("Server returned HTTP ${response.code}: ${response.message}")
        }

        val body = response.body
          ?: throw IOException("Empty response received from media server")

        val responseMime = response.header("Content-Type")?.substringBefore(';') ?: suggestedMime
        if (responseMime != null && responseMime.contains("text/html", ignoreCase = true)) {
          throw IOException("Server returned an HTML webpage instead of a media file.")
        }

        val contentLength = body.contentLength()
        if (contentLength > maxFileSizeBytes) {
          throw IOException("Media size exceeds maximum allowed size of ${maxFileSizeBytes / (1024 * 1024)} MB.")
        }

        var bytesCopied = 0L
        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(tempFile)

        val buffer = ByteArray(8192)

        try {
          outputStream.use { out ->
            inputStream.use { input ->
              var read: Int
              var lastReportedBytes = 0L

              while (input.read(buffer).also { read = it } != -1) {
                currentCoroutineContext().ensureActive()

                out.write(buffer, 0, read)
                bytesCopied += read

                if (bytesCopied > maxFileSizeBytes) {
                  throw IOException("Media size exceeded maximum allowed limit during transfer.")
                }

                // Emit progress every ~32KB or when complete
                if (bytesCopied - lastReportedBytes >= 32768L || (contentLength > 0 && bytesCopied == contentLength)) {
                  val percentage = if (contentLength > 0) {
                    (bytesCopied.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                  } else null

                  emit(DownloadStep.Progress(DownloadProgress(bytesCopied, contentLength, percentage)))
                  lastReportedBytes = bytesCopied
                }
              }
              out.flush()
            }
          }
        } catch (e: Exception) {
          // Cleanup partial file on error
          if (tempFile.exists()) tempFile.delete()
          throw e
        }

        // Validate final file
        if (!tempFile.exists() || tempFile.length() == 0L) {
          if (tempFile.exists()) tempFile.delete()
          throw IOException("Downloaded file was empty.")
        }

        // HTML rejection check on actual bytes
        if (MediaDetector.isHtml(tempFile)) {
          tempFile.delete()
          throw IOException("Downloaded content is an HTML webpage rather than a media file.")
        }

        // Detect real MIME type from magic bytes
        val finalMime = MediaDetector.detectMimeType(tempFile, responseMime)
        if (finalMime.startsWith("text/")) {
          tempFile.delete()
          throw IOException("Downloaded content is text rather than media ($finalMime).")
        }

        // Resolve clean final filename
        val rawName = suggestedFileName ?: mediaUrl.substringBefore('?').substringAfterLast('/')
        val sanitizedName = MediaDetector.sanitizeFileName(rawName, finalMime)
        val finalFile = MediaDetector.createUniqueFile(targetDirectory, sanitizedName)

        val renamed = tempFile.renameTo(finalFile)
        if (!renamed) {
          // Fallback copy if rename fails
          tempFile.copyTo(finalFile, overwrite = true)
          tempFile.delete()
        }

        val isVideo = finalMime.startsWith("video/")
        val isImage = finalMime.startsWith("image/")

        val downloadedItem = DownloadedItem(
          id = UUID.randomUUID().toString(),
          fileName = finalFile.name,
          filePath = finalFile.absolutePath,
          sourceUrl = mediaUrl,
          mimeType = finalMime,
          sizeBytes = finalFile.length(),
          downloadTimestamp = System.currentTimeMillis(),
          isVideo = isVideo,
          isImage = isImage
        )

        emit(DownloadStep.Finished(downloadedItem))
      }
    } catch (e: Exception) {
      if (tempFile.exists()) tempFile.delete()
      throw e
    }
  }.flowOn(Dispatchers.IO)
}
