package com.trikset.gamepad2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.trikset.gamepad2.diagnostics.DiagnosticsReport
import com.trikset.gamepad2.diagnostics.ReportSharer
import com.trikset.gamepad2.diagnostics.ReportZipWriter
import java.io.File

/**
 * Exported image share target (the "attach to report" entry in other apps' share sheets): receives
 * one or more photos/screenshots and packs them together with a fresh diagnostic report into a
 * single ZIP ([ReportZipWriter]), then hands the archive to the system share sheet pre-addressed to
 * support ([ReportSharer.shareZip], `[report]` subject kind). The ZIP is built in [ReportZipWriter]
 * and forwarded as `application/zip`, so the report and the images travel as one mail attachment.
 */
class ShareReceiverActivity : AppCompatActivity() {

  /**
   * Device-only forwarding seam: the default calls [ReportSharer.shareZip], which needs a
   * FileProvider URI (unsupported under Robolectric — see TESTING.md suppression registry / MEMORY
   * "Report file + share"). Robolectric tests replace this field before the packing runnable runs.
   */
  internal var forwardZip: (Context, String, File) -> Unit = { context, reportText, zipFile ->
    ReportSharer.shareZip(context, reportText, zipFile)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val uris = incomingImageUris(intent)
    setContentView(R.layout.activity_share_receiver)
    // Deferred: pack after the "Packing report…" state has rendered (and finish after the runnable,
    // so Robolectric can drive the same path through the main looper).
    window.decorView.post {
      packAndForward(uris)
    }
  }

  internal fun packAndForward(uris: List<Uri>) {
    val reportText = DiagnosticsReport.fromAppState(this)
    val zipFile = ReportZipWriter.pack(this, reportText, uris)
    if (zipFile != null) {
      forwardZip(this, reportText, zipFile)
    }
    finish()
  }

  /**
   * The content URIs attached to the incoming share intent: EXTRA_STREAM (single Uri for
   * ACTION_SEND, a list for ACTION_SEND_MULTIPLE) plus the ClipData items the system puts on the
   * target intent, deduplicated.
   */
  internal fun incomingImageUris(intent: Intent?): List<Uri> {
    val result = LinkedHashSet<Uri>()
    if (intent?.clipData != null) {
      for (i in 0 until intent.clipData!!.itemCount) {
        intent.clipData!!.getItemAt(i).uri?.let(result::add)
      }
    }
    // The typed getParcelableExtra overloads are API 33+; minSdk 23 only has the deprecated forms
    // (registered in TESTING.md "Compiler warnings as errors").
    @Suppress("DEPRECATION")
    fun streamUris(): List<Uri> =
        when (intent?.action) {
          Intent.ACTION_SEND ->
              intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::listOf) ?: emptyList()
          Intent.ACTION_SEND_MULTIPLE ->
              intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
          else -> emptyList()
        }
    result.addAll(streamUris())
    return result.toList()
  }
}
