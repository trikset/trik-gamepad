package com.trikset.gamepad2

import android.content.Intent
import android.net.Uri
import com.trikset.gamepad2.diagnostics.ReportZipTestSupport
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ShareReceiverActivityTest : RobolectricTestBase() {

  private fun registerImage(uri: Uri, content: String) =
      ReportZipTestSupport.registerImage(RuntimeEnvironment.getApplication(), uri, content)

  private fun zipEntries(zipFile: File): Map<String, String> =
      ReportZipTestSupport.zipEntries(zipFile)

  private fun capturedZipAfterPacking(intent: Intent): Pair<String, File> {
    val controller = Robolectric.buildActivity(ShareReceiverActivity::class.java, intent)
    val activity = controller.get()
    var captured: Pair<String, File>? = null
    setField(activity, "forwardZip") { _: android.content.Context, text: String, file: File ->
      captured = text to file
    }
    controller.setup()
    // packAndForward is posted on the decor view (packing state), so run the queued runnable.
    shadowOf(android.os.Looper.getMainLooper()).idle()
    assertNotNull("forwardZip must have been called after the packing runnable", captured)
    return captured!!
  }

  @Before
  fun cleanSharedPreferences() {
    androidx.preference.PreferenceManager.getDefaultSharedPreferences(
            RuntimeEnvironment.getApplication()
        )
        .edit()
        .clear()
        .commit()
  }

  @Test
  fun singleSharedImagePacksReportWithTheImageAndForwards() {
    val uri = Uri.parse("content://com.trikset.gamepad2.test/share/screenshot.png")
    registerImage(uri, "png-bytes")
    val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = "image/png"
          putExtra(Intent.EXTRA_STREAM, uri)
        }

    val (reportText, zipFile) = capturedZipAfterPacking(intent)

    assertTrue(reportText.contains("# TRIK Gamepad diagnostic report"))
    val entries = zipEntries(zipFile)
    assertTrue("report.md must be the archive root entry", entries.containsKey("report.md"))
    assertEquals("png-bytes", entries["attachments/screenshot.png"])
    assertTrue(zipFile.name.endsWith(".zip"))
  }

  @Test
  fun multipleSharedImagesAllTravelAsAttachments() {
    val one = Uri.parse("content://com.trikset.gamepad2.test/share/a.png")
    val two = Uri.parse("content://com.trikset.gamepad2.test/share/b.png")
    registerImage(one, "a-bytes")
    registerImage(two, "b-bytes")
    val streams = ArrayList<Uri>()
    streams.add(one)
    streams.add(two)
    val intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
          type = "image/*"
          putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
        }

    val (_, zipFile) = capturedZipAfterPacking(intent)

    val entries = zipEntries(zipFile)
    assertEquals("a-bytes", entries["attachments/a.png"])
    assertEquals("b-bytes", entries["attachments/b.png"])
  }

  @Test
  fun sharingWithoutImageStreamFinishesWithoutForwarding() {
    val controller =
        Robolectric.buildActivity(
            ShareReceiverActivity::class.java,
            Intent(Intent.ACTION_SEND).apply { type = "text/plain" },
        )
    var forwarded = false
    val activity = controller.get()
    setField(activity, "forwardZip") { _: android.content.Context, _: String, _: File ->
      forwarded = true
    }
    controller.setup()
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertTrue("no stream -> nothing to pack, activity must finish", activity.isFinishing)
    assertEquals("no images -> forward must not run", false, forwarded)
  }

  @Test
  fun sharedImageViaClipDataPacksAndForwards() {
    val uri = Uri.parse("content://com.trikset.gamepad2.test/share/clip.png")
    registerImage(uri, "clip-bytes")
    val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = "image/png"
          // On modern Android the system hands the target intent the URIs in ClipData rather
          // than (only) EXTRA_STREAM — both must be honored. A URI-less clip item (an intent-only
          // row) must be skipped, not crash the unpacking.
          val uriItem = android.content.ClipData.Item(uri)
          val intentOnlyItem = android.content.ClipData.Item(Intent())
          clipData =
              android.content.ClipData("share", arrayOf("image/png"), uriItem).apply {
                addItem(intentOnlyItem)
              }
        }

    val (_, zipFile) = capturedZipAfterPacking(intent)

    val entries = zipEntries(zipFile)
    assertEquals("clip-bytes", entries["attachments/clip.png"])
  }

  @Test
  fun sameUriFromStreamAndClipDataIsPackedOnce() {
    val uri = Uri.parse("content://com.trikset.gamepad2.test/share/dup.png")
    registerImage(uri, "dup-bytes")
    val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = "image/png"
          putExtra(Intent.EXTRA_STREAM, uri)
          clipData =
              android.content.ClipData.newUri(
                  RuntimeEnvironment.getApplication().contentResolver,
                  "share",
                  uri,
              )
        }

    val (_, zipFile) = capturedZipAfterPacking(intent)

    val entries = zipEntries(zipFile)
    assertEquals("a duplicated URI must attach once", 1, entries.size - 1)
    assertEquals("dup-bytes", entries["attachments/dup.png"])
  }

  @Test
  fun incomingImageUrisReadsSingleStream() {
    val uri = Uri.parse("content://com.trikset.gamepad2.test/share/only.png")
    val intent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri) }
    val activity = Robolectric.buildActivity(ShareReceiverActivity::class.java).get()

    assertEquals(listOf(uri), activity.incomingImageUris(intent))
    assertEquals(emptyList<Uri>(), activity.incomingImageUris(null))
  }
}
