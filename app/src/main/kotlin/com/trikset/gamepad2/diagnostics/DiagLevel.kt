package com.trikset.gamepad2.diagnostics

import android.util.Log

/**
 * Maps the user-facing "Diagnostics verbosity" setting values to the [AppLog.minBufferLevel] buffer
 * filter. The captured level is the floor: WARN captures errors and warnings only, DEBUG adds the
 * per-command trace, VERBOSE adds the parser skip/frame-reuse details. Unknown values fall back to
 * [Log.INFO] so a stale or hand-edited setting degrades to the safe default.
 */
object DiagLevel {
  const val KEY_ERRORS = "errors"
  const val KEY_INFO = "info"
  const val KEY_DEBUG = "debug"
  const val KEY_VERBOSE = "verbose"

  fun toBufferLevel(value: String?): Int =
      when (value) {
        KEY_ERRORS -> Log.WARN
        KEY_DEBUG -> Log.DEBUG
        KEY_VERBOSE -> Log.VERBOSE
        else -> Log.INFO
      }
}
