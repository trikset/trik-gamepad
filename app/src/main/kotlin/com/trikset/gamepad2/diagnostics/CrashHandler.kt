package com.trikset.gamepad2.diagnostics

import android.util.Log

/**
 * Process-level uncaught-exception handler installed by [com.trikset.gamepad2.App]: persists the
 * stack trace via [CrashLogStore] (so the next launch can offer to share it) and then delegates to
 * the previous handler so the default kill-and-dump behavior is preserved. Capturing must never
 * fail the crash path itself, so the save is defensive.
 */
class CrashHandler(
    private val store: CrashLogStore,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

  @Suppress("TooGenericExceptionCaught") // the crash path must never fail because capture did
  override fun uncaughtException(thread: Thread, throwable: Throwable) {
    try {
      store.save(Log.getStackTraceString(throwable))
    } catch (e: Exception) {
      Log.w(TAG, "Failed to persist crash report", e)
    }
    previous?.uncaughtException(thread, throwable)
  }

  private companion object {
    const val TAG = "CrashHandler"
  }
}
