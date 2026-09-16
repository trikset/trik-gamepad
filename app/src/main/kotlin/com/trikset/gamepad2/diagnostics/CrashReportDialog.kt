package com.trikset.gamepad2.diagnostics

import android.app.Activity
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.ConnectionState
import com.trikset.gamepad2.R
import com.trikset.gamepad2.SettingsFragment
import com.trikset.gamepad2.copyToClipboard

/**
 * Owns the next-launch crash-report dialog: surfaces a captured crash exactly once, offering to
 * share the report via the text-editor flow (or the direct share sheet when "Share without editing"
 * is set), copy it, or dismiss. Extracted from MainActivity so the dialog logic stays out of the
 * activity and the [connectionState] provider is injectable for tests.
 */
class CrashReportDialog(
    private val activity: Activity,
    private val store: CrashLogStore,
    private val connectionState: () -> ConnectionState?,
) {

  fun showIfNeeded() {
    if (!store.shouldPrompt()) {
      return
    }
    store.markPrompted()
    val crash = store.latest() ?: return
    val shareWithoutEditing =
        PreferenceManager.getDefaultSharedPreferences(activity)
            .getBoolean(SettingsFragment.SK_SHARE_WITHOUT_EDITING, true)
    val reportText = buildReport(crash.stackTrace)
    AlertDialog.Builder(activity)
        .setTitle(R.string.crash_dialog_title)
        .setMessage(R.string.crash_dialog_message)
        .setPositiveButton(
            if (shareWithoutEditing) R.string.share else R.string.review_and_share
        ) { _, _ ->
          ReportSharer.share(activity, reportText, crash = true)
        }
        .setNeutralButton(android.R.string.copy) { _, _ -> copyReport(reportText) }
        .setNegativeButton(R.string.dismiss, null)
        .show()
  }

  private fun buildReport(crashTrace: String): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    val systemInsets = buildSystemInsets()
    val hudBounds = buildHudBounds()
    return DiagnosticsReport.build(
        activity,
        prefs,
        connectionState(),
        AppLog.tail(AppLog.BUFFER_CAPACITY),
        crashTrace,
        systemInsets,
        hudBounds,
    )
  }

  private fun buildSystemInsets(): String? {
    val systemInsets =
        ViewCompat.getRootWindowInsets(activity.window.decorView)
            ?.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.systemGestures() or
                    WindowInsetsCompat.Type.mandatorySystemGestures()
            ) ?: return null
    return "${systemInsets.top}, ${systemInsets.bottom}, ${systemInsets.left}, ${systemInsets.right}"
  }

  @Suppress("ReturnCount") // each missing HUD view is a real recovery edge
  private fun buildHudBounds(): String? {
    fun viewBounds(id: Int): String? {
      val v = activity.findViewById<View>(id) ?: return null
      return "${v.left},${v.top},${v.width},${v.height}"
    }
    val chip = viewBounds(R.id.targetChip) ?: return null
    val leftPad = viewBounds(R.id.leftPad) ?: return null
    val rightPad = viewBounds(R.id.rightPad) ?: return null
    val buttons = viewBounds(R.id.buttons) ?: return null
    return "chip=($chip) leftPad=($leftPad) rightPad=($rightPad) buttons=($buttons)"
  }

  private fun copyReport(reportText: String) {
    activity.copyToClipboard(ReportShareContent.subject(crash = true), reportText)
  }
}
