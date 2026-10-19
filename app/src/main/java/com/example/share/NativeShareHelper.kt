package com.example.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.model.DownloadedItem
import java.io.File

object NativeShareHelper {

  fun shareFile(context: Context, item: DownloadedItem) {
    val file = File(item.filePath)
    if (!file.exists()) {
      Toast.makeText(context, "File no longer exists on disk", Toast.LENGTH_SHORT).show()
      return
    }

    try {
      val authority = "${context.packageName}.fileprovider"
      val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType
        putExtra(Intent.EXTRA_STREAM, contentUri)
        clipData = ClipData.newUri(context.contentResolver, item.fileName, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(
        shareIntent,
        context.getString(R.string.share_chooser_title)
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

      context.startActivity(chooser)
    } catch (e: Exception) {
      Toast.makeText(context, "Failed to launch share sheet: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
  }

  fun openFile(context: Context, item: DownloadedItem) {
    val file = File(item.filePath)
    if (!file.exists()) {
      Toast.makeText(context, "File no longer exists on disk", Toast.LENGTH_SHORT).show()
      return
    }

    try {
      val authority = "${context.packageName}.fileprovider"
      val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

      val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, item.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

      context.startActivity(viewIntent)
    } catch (e: Exception) {
      Toast.makeText(context, "No app found to open this media file", Toast.LENGTH_SHORT).show()
    }
  }
}
