package com.trikset.gamepad2.diagnostics

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class ReportZipWriterTest {

  private fun context(): Context = RuntimeEnvironment.getApplication()

  private fun zipEntries(zipFile: File): Map<String, String> =
      ReportZipTestSupport.zipEntries(zipFile)

  private fun registerImage(uri: Uri, content: String) =
      ReportZipTestSupport.registerImage(context(), uri, content)

  @Test
  fun writePutsReportAtRootAndAttachmentsUnderFolder() {
    val report = "# TRIK Gamepad diagnostic report\n"
    val file =
        ReportZipWriter.write(
            context(),
            report,
            listOf(
                ReportZipWriter.Attachment("shot1.png", "png-one".toByteArray()),
                ReportZipWriter.Attachment("shot2.png", "png-two".toByteArray()),
            ),
        )

    val entries = zipEntries(file)
    assertEquals(report, entries["report.md"])
    assertEquals("png-one", entries["attachments/shot1.png"])
    assertEquals("png-two", entries["attachments/shot2.png"])
    assertTrue(file.name.endsWith(".zip"))
    assertEquals("diagnostics", file.parentFile!!.name)
  }

  @Test
  fun writeDisambiguatesDuplicateDisplayNames() {
    val file =
        ReportZipWriter.write(
            context(),
            "report",
            listOf(
                ReportZipWriter.Attachment("shot.png", "a".toByteArray()),
                ReportZipWriter.Attachment("shot.png", "b".toByteArray()),
            ),
        )

    val entries = zipEntries(file)
    assertEquals("a", entries["attachments/shot.png"])
    assertEquals("b", entries["attachments/shot (1).png"])
  }

  @Test
  fun writeStripsDirectorySegmentsFromDisplayNames() {
    val file =
        ReportZipWriter.write(
            context(),
            "report",
            listOf(ReportZipWriter.Attachment("sub/../shot.png", "a".toByteArray())),
        )

    // Only the last segment survives; the archive entry must never escape attachments/.
    val attachmentEntries = zipEntries(file).keys.filter { it.startsWith("attachments/") }
    assertEquals(listOf("attachments/shot.png"), attachmentEntries)
  }

  @Test
  fun resolveReadsContentAndUsesDisplayNameWhenProviderOffersOne() {
    val authority = "com.trikset.gamepad2.test.gallery"
    val uri = Uri.parse("content://$authority/images/photo123")
    val shadow: ShadowContentResolver = shadowOf(context().contentResolver)
    shadow.registerInputStream(uri, java.io.ByteArrayInputStream("png-bytes".toByteArray()))
    ShadowContentResolver.registerProviderInternal(
        authority,
        object : ContentProvider() {
          override fun onCreate(): Boolean = true

          override fun query(
              uri: Uri,
              projection: Array<out String>?,
              selection: String?,
              selectionArgs: Array<out String>?,
              sortOrder: String?,
          ): Cursor? {
            if (projection?.contains(OpenableColumns.DISPLAY_NAME) == true) {
              return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
                addRow(arrayOf("My Photo.png"))
              }
            }
            return null
          }

          override fun getType(uri: Uri): String? = "image/png"

          override fun insert(uri: Uri, values: ContentValues?): Uri? = null

          override fun delete(
              uri: Uri,
              selection: String?,
              selectionArgs: Array<out String>?,
          ): Int = 0

          override fun update(
              uri: Uri,
              values: ContentValues?,
              selection: String?,
              selectionArgs: Array<out String>?,
          ): Int = 0
        },
    )

    val attachment = ReportZipWriter.resolve(context(), uri, index = 3)

    assertNotNull(attachment)
    assertEquals("My Photo.png", attachment!!.displayName)
    assertEquals("png-bytes", String(attachment.bytes, Charsets.UTF_8))
  }

  @Test
  fun resolveFallsBackToPathSegmentWhenProviderHasNoDisplayName() {
    val shadow: ShadowContentResolver = shadowOf(context().contentResolver)
    val pathUri = Uri.parse("content://com.trikset.gamepad2.test/images/fallback.png")
    shadow.registerInputStream(pathUri, java.io.ByteArrayInputStream("bytes".toByteArray()))
    assertEquals(
        "fallback.png",
        ReportZipWriter.resolve(context(), pathUri, index = 0)!!.displayName,
    )
  }

  @Test
  fun resolveReturnsNullWhenStreamCannotBeOpened() {
    // A provider that answers but whose stream dies mid-read (e.g. a revoked grant): the image
    // must be skipped, not crash the pack.
    val unreadable = Uri.parse("content://com.trikset.gamepad2.test/images/missing")
    val shadow: ShadowContentResolver = shadowOf(context().contentResolver)
    shadow.registerInputStream(
        unreadable,
        object : java.io.InputStream() {
          override fun read(): Int = throw java.io.IOException("grant revoked")
        },
    )
    assertNull(ReportZipWriter.resolve(context(), unreadable, index = 0))
  }

  @Test
  fun resolveReturnsNullOnPermissionDenied() {
    // A share grant that was revoked raises SecurityException on open (Robolectric models that as
    // an input stream whose open is forbidden; simulate with a provider answer).
    val denied = Uri.parse("content://com.trikset.gamepad2.test/images/denied")
    val shadow: ShadowContentResolver = shadowOf(context().contentResolver)
    shadow.registerInputStream(
        denied,
        object : java.io.InputStream() {
          override fun read(): Int = throw SecurityException("no read grant")
        },
    )
    assertNull(ReportZipWriter.resolve(context(), denied, index = 0))
  }

  @Test
  fun packResolvesUrisAndWritesZip() {
    val first = Uri.parse("content://com.trikset.gamepad2.test/gallery/one.png")
    val second = Uri.parse("content://com.trikset.gamepad2.test/gallery/two.png")
    registerImage(first, "bytes-one")
    registerImage(second, "bytes-two")

    val file = ReportZipWriter.pack(context(), "report-text", listOf(first, second))
    assertNotNull("both uris are readable -> a zip must be produced", file)

    val entries = zipEntries(file!!)
    assertEquals("report-text", entries["report.md"])
    assertEquals("bytes-one", entries["attachments/one.png"])
    assertEquals("bytes-two", entries["attachments/two.png"])
  }

  @Test
  fun packSkipsUnreadableUrisButKeepsTheReport() {
    val good = Uri.parse("content://com.trikset.gamepad2.test/gallery/good.png")
    val bad = Uri.parse("content://com.trikset.gamepad2.test/gallery/bad.png")
    registerImage(good, "bytes-good")
    val shadow: ShadowContentResolver = shadowOf(context().contentResolver)
    shadow.registerInputStream(
        bad,
        object : java.io.InputStream() {
          override fun read(): Int = throw java.io.IOException("grant revoked")
        },
    )

    val file = ReportZipWriter.pack(context(), "report-text", listOf(good, bad))
    assertNotNull("at least one uri is readable -> a zip must be produced", file)

    val entries = zipEntries(file!!)
    assertEquals("report-text", entries["report.md"])
    assertEquals("bytes-good", entries["attachments/good.png"])
    assertTrue("unreadable uri must not create an entry", entries.keys.none { it.contains("bad") })
  }

  @Test
  fun packReturnsNullWhenEveryUriIsUnreadable() {
    val only = Uri.parse("content://com.trikset.gamepad2.test/gallery/only.png")
    val shadow: ShadowContentResolver = shadowOf(context().contentResolver)
    shadow.registerInputStream(
        only,
        object : java.io.InputStream() {
          override fun read(): Int = throw java.io.IOException("grant revoked")
        },
    )

    assertNull(ReportZipWriter.pack(context(), "report-text", listOf(only)))
  }
}
