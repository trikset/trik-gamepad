package com.trikset.gamepad2.diagnostics

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.io.File

/**
 * Persists uncaught-crash stack traces to app-internal storage (bounded to [MAX_RECORDS]) so a
 * later launch can offer to share them. Also owns the "prompted once per crash" bookkeeping for the
 * next-launch dialog: [shouldPrompt] is true only for the newest crash that has not been surfaced
 * yet, and [markPrompted] records it.
 */
class CrashLogStore(context: Context) {

  private val dir = File(context.filesDir, CRASH_DIR)
  private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

  data class CrashRecord(val fileName: String, val stackTrace: String)

  fun save(stackTrace: String) {
    if (!dir.exists()) {
      dir.mkdirs()
    }
    // nanoTime is monotonic within the process and on Android grows with device
    // uptime, so the filename sorts the crash records in creation order even for
    // back-to-back saves (wall-clock millis would collide and overwrite).
    val file = File(dir, "crash_${System.nanoTime()}.txt")
    file.writeText(stackTrace, Charsets.UTF_8)
    val records = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
    val toDelete = records.size - MAX_RECORDS
    if (toDelete > 0) {
      records.take(toDelete).forEach { it.delete() }
    }
  }

  /** The newest crash, or null when no crash has been captured. */
  fun latest(): CrashRecord? {
    val newest = dir.listFiles()?.maxByOrNull { it.name } ?: return null
    return CrashRecord(newest.name, newest.readText())
  }

  fun shouldPrompt(): Boolean {
    val newest = latest() ?: return false
    return prefs.getString(PREF_SHOWN_CRASH_FILE, null) != newest.fileName
  }

  fun markPrompted() {
    latest()?.let { prefs.edit { putString(PREF_SHOWN_CRASH_FILE, it.fileName) } }
  }

  companion object {
    const val CRASH_DIR = "crashes"
    private const val MAX_RECORDS = 3
    private const val PREF_SHOWN_CRASH_FILE = "shownCrashFileName"
  }
}
