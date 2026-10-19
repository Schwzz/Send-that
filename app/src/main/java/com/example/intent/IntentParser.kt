package com.example.intent

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.IntentCompat
import com.example.model.SharedInput

object IntentParser {

  private val URL_REGEX = Regex(
    """https?://[^\s<>"'{}|\\^`]+""",
    RegexOption.IGNORE_CASE
  )

  fun parse(intent: Intent?): SharedInput {
    if (intent == null) return SharedInput.Empty

    val action = intent.action
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
      return SharedInput.Empty
    }

    val mimeType = intent.type ?: "*/*"

    // 1. Check for Multiple Streams (ACTION_SEND_MULTIPLE)
    if (action == Intent.ACTION_SEND_MULTIPLE) {
      val streamUris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
      }

      if (!streamUris.isNullOrEmpty()) {
        return SharedInput.MediaUris(streamUris, mimeType)
      }
    }

    // 2. Check for Single Stream (ACTION_SEND)
    val singleUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    if (singleUri != null) {
      return SharedInput.MediaUris(listOf(singleUri), mimeType)
    }

    // 3. Check for Shared Text or URL
    var rawText = intent.getStringExtra(Intent.EXTRA_TEXT)
      ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()

    if (rawText.isNullOrBlank() && intent.clipData != null && intent.clipData!!.itemCount > 0) {
      val item = intent.clipData!!.getItemAt(0)
      rawText = item.text?.toString() ?: item.uri?.toString()
    }

    if (!rawText.isNullOrBlank()) {
      val extractedUrl = extractUrl(rawText)
      return if (extractedUrl != null) {
        SharedInput.WebUrl(url = extractedUrl, rawText = rawText)
      } else {
        SharedInput.Error("The shared text did not contain any valid HTTP or HTTPS link.")
      }
    }

    return SharedInput.Error("Received share intent without text or media stream data.")
  }

  fun extractUrl(text: String): String? {
    return URL_REGEX.find(text)?.value
  }
}
