package com.example.util

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale

object MediaDetector {

  private val HTML_SNIFF_REGEX = Regex(
    """(<!DOCTYPE\s+html|<html[\s>]|<head[\s>]|<body[\s>]|<\?xml)""",
    RegexOption.IGNORE_CASE
  )

  fun isHtml(file: File): Boolean {
    if (!file.exists() || file.length() == 0L) return false
    return try {
      FileInputStream(file).use { input ->
        isHtml(input)
      }
    } catch (_: Exception) {
      false
    }
  }

  fun isHtml(stream: InputStream): Boolean {
    val buffer = ByteArray(512)
    val read = stream.read(buffer)
    if (read <= 0) return false
    val headerText = String(buffer, 0, read, Charsets.UTF_8).trim()
    return HTML_SNIFF_REGEX.containsMatchIn(headerText)
  }

  fun detectMimeType(file: File, fallbackMime: String? = null): String {
    if (!file.exists() || file.length() == 0L) {
      return fallbackMime ?: "application/octet-stream"
    }

    try {
      FileInputStream(file).use { input ->
        val header = ByteArray(32)
        val read = input.read(header)
        if (read >= 3) {
          // JPEG: FF D8 FF
          if ((header[0].toInt() and 0xFF) == 0xFF &&
            (header[1].toInt() and 0xFF) == 0xD8 &&
            (header[2].toInt() and 0xFF) == 0xFF
          ) {
            return "image/jpeg"
          }
        }
        if (read >= 8) {
          // PNG: 89 50 4E 47 0D 0A 1A 0A
          if ((header[0].toInt() and 0xFF) == 0x89 &&
            (header[1].toInt() and 0xFF) == 0x50 &&
            (header[2].toInt() and 0xFF) == 0x4E &&
            (header[3].toInt() and 0xFF) == 0x47
          ) {
            return "image/png"
          }

          // GIF: 47 49 46 38 ("GIF8")
          if (header[0] == 'G'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == '8'.code.toByte()
          ) {
            return "image/gif"
          }

          // WebP: RIFF....WEBP
          if (header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            read >= 12 &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() &&
            header[11] == 'P'.code.toByte()
          ) {
            return "image/webp"
          }
        }

        // MP4 / MOV: offset 4 contains 'ftyp' or 'moov'
        if (read >= 12) {
          val type = String(header, 4, 4, Charsets.US_ASCII)
          if (type == "ftyp" || type == "moov" || type == "wide") {
            return "video/mp4"
          }
        }

        // WebM / Matroska: 1A 45 DF A3
        if (read >= 4) {
          if ((header[0].toInt() and 0xFF) == 0x1A &&
            (header[1].toInt() and 0xFF) == 0x45 &&
            (header[2].toInt() and 0xFF) == 0xDF &&
            (header[3].toInt() and 0xFF) == 0xA3
          ) {
            return "video/webm"
          }
        }
      }
    } catch (_: Exception) {
      // Ignore and use fallback
    }

    if (!fallbackMime.isNullOrBlank() && fallbackMime != "application/octet-stream" && fallbackMime != "text/html") {
      return fallbackMime
    }

    // Fallback by extension
    val ext = file.extension.lowercase(Locale.ROOT)
    return when (ext) {
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "gif" -> "image/gif"
      "webp" -> "image/webp"
      "mp4", "m4v" -> "video/mp4"
      "webm" -> "video/webm"
      "mov" -> "video/quicktime"
      "mkv" -> "video/x-matroska"
      else -> "application/octet-stream"
    }
  }

  fun sanitizeFileName(rawName: String, mimeType: String?): String {
    // Strip query parameters and path elements
    var clean = rawName.substringBefore('?').substringBefore('#')
    val lastSlash = clean.lastIndexOfAny(charArrayOf('/', '\\'))
    if (lastSlash != -1) {
      clean = clean.substring(lastSlash + 1)
    }

    // Replace invalid file system characters with underscore
    clean = clean.replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
    clean = clean.replace(Regex("""\.{2,}"""), ".") // Prevent directory traversal ..

    var ext = clean.substringAfterLast('.', "")
    var nameWithoutExt = clean.substringBeforeLast('.', clean)

    if (nameWithoutExt.isBlank() || nameWithoutExt == "_") {
      nameWithoutExt = "media_${System.currentTimeMillis()}"
    }

    // Truncate name to reasonable length
    if (nameWithoutExt.length > 50) {
      nameWithoutExt = nameWithoutExt.substring(0, 50)
    }

    // Ensure valid extension based on mimeType if missing or invalid
    if (ext.isBlank() || ext == clean) {
      ext = when (mimeType?.lowercase(Locale.ROOT)) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        else -> "bin"
      }
    }

    return "$nameWithoutExt.$ext"
  }

  fun createUniqueFile(parentDir: File, desiredFileName: String): File {
    parentDir.mkdirs()
    var candidate = File(parentDir, desiredFileName)
    if (!candidate.exists()) return candidate

    val baseName = desiredFileName.substringBeforeLast('.')
    val ext = desiredFileName.substringAfterLast('.', "")
    val extSuffix = if (ext.isNotEmpty()) ".$ext" else ""

    var counter = 1
    while (candidate.exists()) {
      candidate = File(parentDir, "${baseName}_$counter$extSuffix")
      counter++
    }
    return candidate
  }
}
