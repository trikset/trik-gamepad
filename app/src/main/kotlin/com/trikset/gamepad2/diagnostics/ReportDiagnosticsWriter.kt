package com.trikset.gamepad2.diagnostics

import android.content.Context
import java.io.File

/**
 * Writes the diagnostic report to the app's cache as a single markdown file so it can be opened in
 * a text editor (for review/edit) or shared as an attachment. The file lives in a dedicated
 * `cacheDir/diagnostics` directory so it can be exposed through the FileProvider paths without
 * granting anything broader; it is ephemeral and regenerated on every tap.
 */
object ReportDiagnosticsWriter {
  const val DIAGNOSTICS_DIR = "diagnostics"
  const val FILE_PREFIX = "trik-gamepad-report"

  fun write(context: Context, reportText: String): File {
    val file = newFile(context, "md")
    file.writeText(reportText, Charsets.UTF_8)
    return file
  }

  /** A fresh timestamped file in the diagnostics dir with the given [extension]. */
  fun newFile(context: Context, extension: String): File {
    val dir = File(context.cacheDir, DIAGNOSTICS_DIR)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return File(dir, "$FILE_PREFIX-${System.currentTimeMillis()}.$extension")
  }

  fun fileProviderAuthority(context: Context): String = context.packageName + ".fileprovider"
}
