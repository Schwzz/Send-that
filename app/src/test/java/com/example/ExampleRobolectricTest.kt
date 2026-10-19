package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.DownloadRepository
import com.example.intent.IntentParser
import com.example.model.DownloadedItem
import com.example.model.SharedInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun testAppNameResourceResolution() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MediaShare", appName)
  }

  @Test
  fun testIntentParserHandlesSendActionWithUrl() {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, "Look at this https://example.com/asset.png")
    }

    val parsed = IntentParser.parse(intent)
    assertTrue(parsed is SharedInput.WebUrl)
    val webUrl = parsed as SharedInput.WebUrl
    assertEquals("https://example.com/asset.png", webUrl.url)
  }

  @Test
  fun testIntentParserHandlesSendActionWithoutUrl() {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, "Just a message with no links")
    }

    val parsed = IntentParser.parse(intent)
    assertTrue(parsed is SharedInput.Error)
  }

  @Test
  fun testIntentParserHandlesMediaStreamUri() {
    val testUri = Uri.parse("content://media/external/images/media/42")
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "image/jpeg"
      putExtra(Intent.EXTRA_STREAM, testUri)
    }

    val parsed = IntentParser.parse(intent)
    assertTrue(parsed is SharedInput.MediaUris)
    val mediaUris = parsed as SharedInput.MediaUris
    assertEquals(1, mediaUris.uris.size)
    assertEquals(testUri, mediaUris.uris.first())
  }

  @Test
  fun testIntentParserHandlesMultipleMediaStreamUris() {
    val testUri1 = Uri.parse("content://media/external/images/media/101")
    val testUri2 = Uri.parse("content://media/external/images/media/102")
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
      type = "image/jpeg"
      putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(testUri1, testUri2))
    }

    val parsed = IntentParser.parse(intent)
    assertTrue(parsed is SharedInput.MediaUris)
    val mediaUris = parsed as SharedInput.MediaUris
    assertEquals(2, mediaUris.uris.size)
  }

  @Test
  fun testDownloadRepositoryPersistenceAndDeletion() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = DownloadRepository(context)

    // Create a real file in repo's download dir
    val testFile = File(repo.downloadsDir, "sample_saved.jpg")
    testFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))

    val item = DownloadedItem(
      id = "test-123",
      fileName = testFile.name,
      filePath = testFile.absolutePath,
      sourceUrl = "https://example.com/sample.jpg",
      mimeType = "image/jpeg",
      sizeBytes = testFile.length(),
      downloadTimestamp = System.currentTimeMillis(),
      isVideo = false,
      isImage = true
    )

    repo.saveDownloadedItem(item)
    assertTrue(repo.downloads.value.any { it.id == item.id })

    // Test deletion
    val deleted = repo.deleteItem(item)
    assertTrue(deleted)
    assertFalse(testFile.exists())
    assertFalse(repo.downloads.value.any { it.id == item.id })
  }
}
