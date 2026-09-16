package com.trikset.gamepad2.diagnostics

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile

/** Shared helpers for tests that read ZIPs produced by [ReportZipWriter] or register image URIs. */
object ReportZipTestSupport {

  fun zipEntries(zipFile: File): Map<String, String> {
    val entries = LinkedHashMap<String, String>()
    ZipFile(zipFile).use { zip ->
      zip.entries().asSequence().forEach { entry ->
        entries[entry.name] = zip.getInputStream(entry).bufferedReader().readText()
      }
    }
    return entries
  }

  fun registerImage(context: Context, uri: Uri, content: String) {
    org.robolectric.Shadows.shadowOf(context.contentResolver)
        .registerInputStream(uri, ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))
  }
}
