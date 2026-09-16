package com.trikset.gamepad2.diagnostics

import android.os.Build
import com.trikset.gamepad2.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the share "cover letter" for every diagnostic report the user sends: the mail [subject]
 * (used as the mail subject and as the clipboard label on copy), the [SUPPORT_EMAIL] the share is
 * addressed to, and the bounded [messageBody] (the report head before the `## Settings` section) so
 * the note fits messenger captions while the full report travels as the attachment.
 *
 * The subject deliberately reads like a machine tag, not a human sentence: developers group tickets
 * by the `[trik-gamepad][crash|report]` prefix. The kind is chosen at the call site ([crash] for
 * the crash dialog, [report] for the manual "Report an issue" row) — never inferred from the report
 * text, because a manual report may still embed the last crash trace.
 */
object ReportShareContent {
  const val SUPPORT_EMAIL = "support@trikset.com"

  private const val SETTINGS_HEADING = "## Settings"

  fun subject(crash: Boolean, nowMs: Long = System.currentTimeMillis()): String {
    val kind = if (crash) "crash" else "report"
    val stamp =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(nowMs))
    return "[trik-gamepad][$kind] v${BuildConfig.VERSION_NAME}, " +
        "API ${Build.VERSION.SDK_INT}, ${Build.MODEL}, $stamp"
  }

  /** Everything before the `## Settings` section (App/Device/Display/Connection), trimmed. */
  fun headSnippet(reportText: String): String {
    val settingsIndex = reportText.indexOf(SETTINGS_HEADING)
    val head = if (settingsIndex >= 0) reportText.substring(0, settingsIndex) else reportText
    return head.trim()
  }

  fun messageBody(
      reportText: String,
      crash: Boolean,
      nowMs: Long = System.currentTimeMillis(),
  ): String = subject(crash, nowMs) + "\n\n" + headSnippet(reportText)
}
