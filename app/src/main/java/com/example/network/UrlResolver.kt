package com.example.network

import com.example.util.MediaDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface ResolutionResult {
  data class DirectMedia(val url: String, val suggestedMime: String?) : ResolutionResult
  data class ResolvedWebMedia(val directUrl: String, val suggestedMime: String?, val title: String?) : ResolutionResult
  data class AuthRequiredOrBlocked(val message: String) : ResolutionResult
  data class NoMediaFound(val message: String) : ResolutionResult
  data class NetworkError(val message: String) : ResolutionResult
}

class UrlResolver(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()
) {

  private val DIRECT_EXTENSIONS = listOf(
    ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
    ".mp4", ".webm", ".mkv", ".mov", ".m4v"
  )

  private val OG_VIDEO_REGEX = Regex(
    """<meta\s+[^>]*property=["'](?:og:video:secure_url|og:video)["'][^>]*content=["']([^"']+)["']""",
    RegexOption.IGNORE_CASE
  )
  private val OG_IMAGE_REGEX = Regex(
    """<meta\s+[^>]*property=["'](?:og:image:secure_url|og:image)["'][^>]*content=["']([^"']+)["']""",
    RegexOption.IGNORE_CASE
  )
  private val TWITTER_STREAM_REGEX = Regex(
    """<meta\s+[^>]*name=["'](?:twitter:player:stream|twitter:image)["'][^>]*content=["']([^"']+)["']""",
    RegexOption.IGNORE_CASE
  )
  private val TITLE_REGEX = Regex(
    """<title[^>]*>([^<]+)</title>""",
    RegexOption.IGNORE_CASE
  )

  suspend fun resolve(rawUrl: String): ResolutionResult = withContext(Dispatchers.IO) {
    val cleanUrl = rawUrl.trim()
    if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
      !cleanUrl.startsWith("https://", ignoreCase = true)
    ) {
      return@withContext ResolutionResult.NoMediaFound("Invalid URL scheme. Must be http or https.")
    }

    // 1. Check quick file extension
    val pathPart = cleanUrl.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    if (DIRECT_EXTENSIONS.any { pathPart.endsWith(it) }) {
      return@withContext ResolutionResult.DirectMedia(cleanUrl, guessMimeFromUrl(cleanUrl))
    }

    // 2. Perform a HEAD request to check Content-Type
    try {
      val headRequest = Request.Builder()
        .url(cleanUrl)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        .head()
        .build()

      val headResponse = try {
        client.newCall(headRequest).execute()
      } catch (_: Exception) {
        null
      }

      if (headResponse != null && headResponse.isSuccessful) {
        val contentType = headResponse.header("Content-Type")?.lowercase(Locale.ROOT) ?: ""
        headResponse.close()

        if (contentType.startsWith("image/") || contentType.startsWith("video/")) {
          return@withContext ResolutionResult.DirectMedia(cleanUrl, contentType.substringBefore(';'))
        }
      } else {
        headResponse?.close()
      }

      // 3. If not direct media or HEAD failed/returned HTML, do a GET to inspect page metadata
      val getRequest = Request.Builder()
        .url(cleanUrl)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/*,video/*;q=0.8")
        .build()

      client.newCall(getRequest).execute().use { response ->
        val code = response.code
        val finalUrl = response.request.url.toString()

        // Check for authentication wall
        if (code == 401 || code == 403 || finalUrl.contains("/login") || finalUrl.contains("accounts.google") || finalUrl.contains("instagram.com/accounts/login")) {
          return@withContext ResolutionResult.AuthRequiredOrBlocked(
            "This platform requires user authentication or restricts automated downloads (HTTP $code)."
          )
        }

        if (!response.isSuccessful) {
          return@withContext ResolutionResult.NetworkError("Server returned HTTP $code: ${response.message}")
        }

        val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT) ?: ""
        if (contentType.startsWith("image/") || contentType.startsWith("video/")) {
          return@withContext ResolutionResult.DirectMedia(finalUrl, contentType.substringBefore(';'))
        }

        // Limit reading HTML to max 256 KB
        val body = response.body
          ?: return@withContext ResolutionResult.NoMediaFound("Empty response received from server.")

        val source = body.source()
        val buffer = okio.Buffer()
        var readTotal = 0L
        val maxHtmlBytes = 256 * 1024L
        while (!source.exhausted() && readTotal < maxHtmlBytes) {
          val count = source.read(buffer, 8192)
          if (count == -1L) break
          readTotal += count
        }
        val html = buffer.readUtf8()

        val title = TITLE_REGEX.find(html)?.groupValues?.get(1)?.trim()

        // Extract video candidate
        val videoMatch = OG_VIDEO_REGEX.find(html) ?: TWITTER_STREAM_REGEX.find(html)
        if (videoMatch != null) {
          val candidateUrl = normalizeCandidateUrl(cleanUrl, videoMatch.groupValues[1])
          if (verifyMediaEndpoint(candidateUrl)) {
            return@withContext ResolutionResult.ResolvedWebMedia(
              directUrl = candidateUrl,
              suggestedMime = guessMimeFromUrl(candidateUrl) ?: "video/mp4",
              title = title
            )
          }
        }

        // Extract image candidate
        val imageMatch = OG_IMAGE_REGEX.find(html)
        if (imageMatch != null) {
          val candidateUrl = normalizeCandidateUrl(cleanUrl, imageMatch.groupValues[1])
          if (verifyMediaEndpoint(candidateUrl)) {
            return@withContext ResolutionResult.ResolvedWebMedia(
              directUrl = candidateUrl,
              suggestedMime = guessMimeFromUrl(candidateUrl) ?: "image/jpeg",
              title = title
            )
          }
        }

        return@withContext ResolutionResult.NoMediaFound(
          "The webpage was reached successfully, but no direct public video or image stream was exposed."
        )
      }
    } catch (e: IOException) {
      return@withContext ResolutionResult.NetworkError("Network request failed: ${e.localizedMessage ?: "Connection error"}")
    } catch (e: Exception) {
      return@withContext ResolutionResult.NetworkError("Error inspecting link: ${e.localizedMessage ?: "Unexpected error"}")
    }
  }

  private fun verifyMediaEndpoint(candidateUrl: String): Boolean {
    return try {
      val request = Request.Builder()
        .url(candidateUrl)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
        .head()
        .build()

      client.newCall(request).execute().use { resp ->
        val type = resp.header("Content-Type")?.lowercase(Locale.ROOT) ?: ""
        resp.isSuccessful && (type.startsWith("image/") || type.startsWith("video/") || type == "application/octet-stream")
      }
    } catch (_: Exception) {
      // If HEAD is blocked or fails, still allow attempting download if URL looks like media
      DIRECT_EXTENSIONS.any { candidateUrl.substringBefore('?').lowercase(Locale.ROOT).endsWith(it) }
    }
  }

  private fun normalizeCandidateUrl(basePageUrl: String, rawCandidate: String): String {
    val unescaped = rawCandidate.replace("&amp;", "&")
    return if (unescaped.startsWith("//")) {
      "https:$unescaped"
    } else if (unescaped.startsWith("/")) {
      try {
        val uri = java.net.URI(basePageUrl)
        val portSuffix = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
        "${uri.scheme}://${uri.host}$portSuffix$unescaped"
      } catch (_: Exception) {
        unescaped
      }
    } else unescaped
  }

  private fun guessMimeFromUrl(url: String): String? {
    val clean = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    return when {
      clean.endsWith(".jpg") || clean.endsWith(".jpeg") -> "image/jpeg"
      clean.endsWith(".png") -> "image/png"
      clean.endsWith(".gif") -> "image/gif"
      clean.endsWith(".webp") -> "image/webp"
      clean.endsWith(".mp4") -> "video/mp4"
      clean.endsWith(".webm") -> "video/webm"
      clean.endsWith(".mov") -> "video/quicktime"
      else -> null
    }
  }
}
