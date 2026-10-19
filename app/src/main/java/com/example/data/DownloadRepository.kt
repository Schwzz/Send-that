package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.DownloadedItem
import com.example.util.MediaDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DownloadRepository(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

  val downloadsDir: File = File(context.filesDir, "downloads").apply { mkdirs() }
  private val indexFile: File = File(context.filesDir, "downloads_index.json")

  private val _downloads = MutableStateFlow<List<DownloadedItem>>(emptyList())
  val downloads: StateFlow<List<DownloadedItem>> = _downloads.asStateFlow()

  private val mutex = Mutex()

  init {
    scope.launch {
      reloadFromDisk()
    }
  }

  suspend fun reloadFromDisk() = withContext(Dispatchers.IO) {
    mutex.withLock {
      val loaded = readIndexFile()
      // Reconcile: verify files actually exist on disk
      val verified = loaded.filter { item ->
        val file = File(item.filePath)
        file.exists() && file.length() > 0
      }

      // Also check if any loose files exist in downloadsDir not in index
      val diskFiles = downloadsDir.listFiles { f -> f.isFile && !f.name.endsWith(".part") && !f.name.endsWith(".tmp") } ?: emptyArray()
      val indexedPaths = verified.map { it.filePath }.toSet()

      val unindexedItems = diskFiles.filter { it.absolutePath !in indexedPaths }.map { file ->
        val mime = MediaDetector.detectMimeType(file)
        DownloadedItem(
          id = UUID.randomUUID().toString(),
          fileName = file.name,
          filePath = file.absolutePath,
          sourceUrl = null,
          mimeType = mime,
          sizeBytes = file.length(),
          downloadTimestamp = file.lastModified(),
          isVideo = mime.startsWith("video/"),
          isImage = mime.startsWith("image/")
        )
      }

      val all = (verified + unindexedItems).sortedByDescending { it.downloadTimestamp }
      _downloads.value = all
      writeIndexFile(all)
    }
  }

  suspend fun saveDownloadedItem(item: DownloadedItem) = withContext(Dispatchers.IO) {
    mutex.withLock {
      val current = _downloads.value.filter { it.id != item.id && it.filePath != item.filePath }
      val updated = (listOf(item) + current).sortedByDescending { it.downloadTimestamp }
      _downloads.value = updated
      writeIndexFile(updated)
    }
  }

  suspend fun importMediaUri(uri: Uri, contentResolver: ContentResolver): DownloadedItem = withContext(Dispatchers.IO) {
    downloadsDir.mkdirs()

    val rawDisplayName = queryDisplayName(contentResolver, uri) ?: "shared_${System.currentTimeMillis()}"
    val mimeType = contentResolver.getType(uri) ?: MediaDetector.detectMimeType(File(rawDisplayName))

    val sanitizedName = MediaDetector.sanitizeFileName(rawDisplayName, mimeType)
    val destFile = MediaDetector.createUniqueFile(downloadsDir, sanitizedName)

    contentResolver.openInputStream(uri)?.use { input ->
      FileOutputStream(destFile).use { output ->
        input.copyTo(output)
        output.flush()
      }
    } ?: throw java.io.IOException("Cannot open stream for content URI: $uri")

    // Sniff real mime type from file
    val actualMime = MediaDetector.detectMimeType(destFile, mimeType)
    val item = DownloadedItem(
      id = UUID.randomUUID().toString(),
      fileName = destFile.name,
      filePath = destFile.absolutePath,
      sourceUrl = uri.toString(),
      mimeType = actualMime,
      sizeBytes = destFile.length(),
      downloadTimestamp = System.currentTimeMillis(),
      isVideo = actualMime.startsWith("video/"),
      isImage = actualMime.startsWith("image/")
    )

    saveDownloadedItem(item)
    item
  }

  suspend fun deleteItem(item: DownloadedItem): Boolean = withContext(Dispatchers.IO) {
    mutex.withLock {
      val file = File(item.filePath)
      var deleted = true
      if (file.exists()) {
        deleted = file.delete()
      }

      val updated = _downloads.value.filter { it.id != item.id && it.filePath != item.filePath }
      _downloads.value = updated
      writeIndexFile(updated)
      deleted
    }
  }

  suspend fun clearAll() = withContext(Dispatchers.IO) {
    mutex.withLock {
      _downloads.value.forEach { item ->
        val file = File(item.filePath)
        if (file.exists()) file.delete()
      }
      _downloads.value = emptyList()
      writeIndexFile(emptyList())
    }
  }

  private fun readIndexFile(): List<DownloadedItem> {
    if (!indexFile.exists() || indexFile.length() == 0L) return emptyList()
    return try {
      val jsonStr = indexFile.readText()
      val array = JSONArray(jsonStr)
      val list = mutableListOf<DownloadedItem>()
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(
          DownloadedItem(
            id = obj.getString("id"),
            fileName = obj.getString("fileName"),
            filePath = obj.getString("filePath"),
            sourceUrl = if (obj.has("sourceUrl") && !obj.isNull("sourceUrl")) obj.getString("sourceUrl") else null,
            mimeType = obj.getString("mimeType"),
            sizeBytes = obj.getLong("sizeBytes"),
            downloadTimestamp = obj.getLong("downloadTimestamp"),
            isVideo = obj.getBoolean("isVideo"),
            isImage = obj.getBoolean("isImage")
          )
        )
      }
      list
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun writeIndexFile(items: List<DownloadedItem>) {
    try {
      val array = JSONArray()
      items.forEach { item ->
        val obj = JSONObject().apply {
          put("id", item.id)
          put("fileName", item.fileName)
          put("filePath", item.filePath)
          put("sourceUrl", item.sourceUrl)
          put("mimeType", item.mimeType)
          put("sizeBytes", item.sizeBytes)
          put("downloadTimestamp", item.downloadTimestamp)
          put("isVideo", item.isVideo)
          put("isImage", item.isImage)
        }
        array.put(obj)
      }
      indexFile.writeText(array.toString(2))
    } catch (_: Exception) {
      // Ignore write errors to prevent crashes
    }
  }

  private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
    return try {
      contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx != -1 && !cursor.isNull(idx)) cursor.getString(idx) else null
        } else null
      }
    } catch (_: Exception) {
      null
    }
  }
}
