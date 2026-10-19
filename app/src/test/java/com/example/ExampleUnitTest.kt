package com.example

import com.example.intent.IntentParser
import com.example.model.SharedInput
import com.example.util.MediaDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class ExampleUnitTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testUrlExtractionFromSurroundingText() {
    val text = "Check out this photo: https://example.com/gallery/sunset.jpg #photography"
    val extracted = IntentParser.extractUrl(text)
    assertEquals("https://example.com/gallery/sunset.jpg", extracted)
  }

  @Test
  fun testUrlExtractionWithNoUrl() {
    val text = "Just some text without any web links"
    val extracted = IntentParser.extractUrl(text)
    assertEquals(null, extracted)
  }

  @Test
  fun testHtmlSniffingDetection() {
    val html1 = "<!DOCTYPE html><html><head><title>Error</title></head><body>Login required</body></html>"
    assertTrue(MediaDetector.isHtml(ByteArrayInputStream(html1.toByteArray(Charsets.UTF_8))))

    val html2 = "<html>\n<head><title>Access Denied</title></head></html>"
    assertTrue(MediaDetector.isHtml(ByteArrayInputStream(html2.toByteArray(Charsets.UTF_8))))

    // Raw binary jpeg should NOT be flagged as HTML
    val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
    assertFalse(MediaDetector.isHtml(ByteArrayInputStream(jpegHeader)))
  }

  @Test
  fun testMagicBytesMimeDetection() {
    val folder = tempFolder.newFolder("mime_test")

    // PNG test
    val pngFile = File(folder, "test.png")
    pngFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00))
    assertEquals("image/png", MediaDetector.detectMimeType(pngFile))

    // JPEG test
    val jpgFile = File(folder, "test.jpg")
    jpgFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDB.toByte()))
    assertEquals("image/jpeg", MediaDetector.detectMimeType(jpgFile))

    // GIF test
    val gifFile = File(folder, "test.gif")
    gifFile.writeBytes("GIF89a".toByteArray(Charsets.US_ASCII))
    assertEquals("image/gif", MediaDetector.detectMimeType(gifFile))

    // MP4 test (offset 4 has 'ftyp')
    val mp4File = File(folder, "test.mp4")
    val mp4Bytes = byteArrayOf(0x00, 0x00, 0x00, 0x20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 0x00, 0x00, 0x00, 0x00)
    mp4File.writeBytes(mp4Bytes)
    assertEquals("video/mp4", MediaDetector.detectMimeType(mp4File))
  }

  @Test
  fun testSanitizeFileName() {
    val sanitized1 = MediaDetector.sanitizeFileName("../../etc/passwd", "image/jpeg")
    assertFalse(sanitized1.contains(".."))
    assertFalse(sanitized1.contains("/"))
    assertTrue(sanitized1.endsWith(".jpg") || sanitized1.endsWith(".jpeg"))

    val sanitized2 = MediaDetector.sanitizeFileName("cool_video.mp4?auth=secret#hash", "video/mp4")
    assertEquals("cool_video.mp4", sanitized2)

    val sanitized3 = MediaDetector.sanitizeFileName("invalid*name:?<>|.png", "image/png")
    assertFalse(sanitized3.contains("*"))
    assertFalse(sanitized3.contains(":"))
    assertTrue(sanitized3.endsWith(".png"))
  }

  @Test
  fun testCreateUniqueFile() {
    val dir = tempFolder.newFolder("unique_test")
    val file1 = MediaDetector.createUniqueFile(dir, "photo.jpg")
    file1.createNewFile()
    assertEquals("photo.jpg", file1.name)

    val file2 = MediaDetector.createUniqueFile(dir, "photo.jpg")
    file2.createNewFile()
    assertEquals("photo_1.jpg", file2.name)

    val file3 = MediaDetector.createUniqueFile(dir, "photo.jpg")
    assertEquals("photo_2.jpg", file3.name)
  }
}
