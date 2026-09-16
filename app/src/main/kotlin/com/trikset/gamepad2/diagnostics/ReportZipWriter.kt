package com.trikset.gamepad2.diagnostics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs a diagnostic report and the images a user shares into the app (from any app's share sheet)
 * into a single ZIP so both travel as one mail attachment: `report.md` at the archive root plus
 * each image under `attachments/` with its original display name (duplicates disambiguated, unsafe
 * path segments stripped). The archive lands in the same `cacheDir/diagnostics` directory as the
 * plain report, so the existing FileProvider path exposes it without a manifest change.
 */
object ReportZipWriter {

  const val REPORT_ENTRY = "report.md"
  private const val ATTACHMENTS_DIR = "attachments"

  data class Attachment(val displayName: String, val bytes: ByteArray)

  /**
   * Resolves every incoming [uris] into an [Attachment] and writes the ZIP (a single helper for the
   * share-in receiver). [reportText] becomes `report.md` at the archive root. Returns null when
   * none of the shared URIs could be read (revoked grant / broken stream) — there is nothing to
   * attach.
   */
  fun pack(context: Context, reportText: String, uris: List<Uri>): File? {
    val attachments = uris.mapIndexedNotNull { index, uri -> resolve(context, uri, index) }
    if (attachments.isEmpty()) {
      return null
    }
    return write(context, reportText, attachments)
  }

  /**
   * Writes the ZIP into a fresh [ReportDiagnosticsWriter.newFile] and returns it. Pure file I/O —
   * the caller resolves [Attachment]s (reading the incoming content URIs) beforehand.
   */
  fun write(context: Context, reportText: String, attachments: List<Attachment>): File {
    val file = ReportDiagnosticsWriter.newFile(context, "zip")
    ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
      zip.putNextEntry(ZipEntry(REPORT_ENTRY))
      zip.write(reportText.toByteArray(Charsets.UTF_8))
      zip.closeEntry()
      val usedNames = HashSet<String>()
      for (attachment in attachments) {
        val name = uniqueName(attachment.displayName, usedNames)
        zip.putNextEntry(ZipEntry("$ATTACHMENTS_DIR/$name"))
        zip.write(attachment.bytes)
        zip.closeEntry()
      }
    }
    return file
  }

  /** Reads one incoming share URI into an [Attachment]; null when the image cannot be read. */
  fun resolve(context: Context, uri: Uri, index: Int): Attachment? {
    val resolver = context.contentResolver
    val bytes =
        try {
          resolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: java.io.IOException) {
          // The sharing app revoked the grant or the file vanished mid-read.
          null
        } catch (_: SecurityException) {
          // Same: a permission error is not a reason to crash the whole share.
          null
        } ?: return null
    val name = displayName(resolver, uri) ?: uri.lastPathSegment ?: "image-$index"
    return Attachment(name, bytes)
  }

  /**
   * Makes [displayName] archive-safe and unique within [used]: strips any directory segments, then
   * appends a numeric suffix (" (1)", " (2)", ...) before the extension when the name is taken.
   */
  internal fun uniqueName(displayName: String, used: MutableSet<String>): String {
    val base = displayName.substringAfterLast('/').ifBlank { "image" }
    val dot = base.lastIndexOf('.')
    val stem = if (dot > 0) base.substring(0, dot) else base
    val ext = if (dot > 0) base.substring(dot) else ""
    var candidate = base
    var suffix = 1
    while (!used.add(candidate)) {
      candidate = "$stem (${suffix++})$ext"
    }
    return candidate
  }

  private fun displayName(resolver: ContentResolver, uri: Uri): String? =
      try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor
          ->
          if (cursor.moveToFirst()) cursor.getString(0) else null
        }
      } catch (_: RuntimeException) {
        // Some content providers reject the DISPLAY_NAME projection; the last path segment
        // (or the numbered fallback) still yields a usable entry name.
        null
      }
}
