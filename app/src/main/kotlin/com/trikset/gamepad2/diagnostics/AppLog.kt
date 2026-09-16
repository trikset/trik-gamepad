package com.trikset.gamepad2.diagnostics

import android.util.Log
import java.util.Locale

/**
 * Central log channel for the app. Every call is mirrored to logcat (gated by the system log level
 * via [Log.isLoggable], so the DEBUG-only verbosity sites keep their current behavior) and, when it
 * passes the buffer filter ([minBufferLevel], default [Log.INFO]), appended to a bounded,
 * synchronized ring buffer ([tail]) so a user-facing diagnostic report can reproduce a bug without
 * adb. The default filter excludes the high-frequency per-command DEBUG trace and keeps only the
 * reproduction-relevant events (connection, keepalive heartbeat, stream errors); users can raise or
 * lower it via the "Diagnostics verbosity" setting.
 */
object AppLog {
  const val BUFFER_CAPACITY = 500

  /** Minimum level captured into the ring buffer (VERBOSE < DEBUG < INFO < WARN < ERROR). */
  @Volatile var minBufferLevel: Int = Log.INFO

  private val ring = LogRingBuffer(BUFFER_CAPACITY)

  fun v(tag: String, msg: String) = log(Log.VERBOSE, tag, msg, null)

  fun d(tag: String, msg: String) = log(Log.DEBUG, tag, msg, null)

  fun d(tag: String, msg: String, t: Throwable) = log(Log.DEBUG, tag, msg, t)

  fun i(tag: String, msg: String) = log(Log.INFO, tag, msg, null)

  fun w(tag: String, msg: String) = log(Log.WARN, tag, msg, null)

  fun e(tag: String, msg: String) = log(Log.ERROR, tag, msg, null)

  fun e(tag: String, msg: String, t: Throwable) = log(Log.ERROR, tag, msg, t)

  /** The newest [count] buffered lines, oldest first; fewer when the buffer is not full yet. */
  fun tail(count: Int): List<String> = ring.tail(count)

  /** Test-only reset: clears the ring buffer. [minBufferLevel] is set by its callers. */
  fun clearForTest() {
    ring.clear()
  }

  private fun log(level: Int, tag: String, msg: String, t: Throwable?) {
    if (level >= minBufferLevel) {
      ring.append(format(level, tag, msg, t))
    }
    if (Log.isLoggable(tag, level)) {
      when (level) {
        Log.VERBOSE -> Log.v(tag, msg)
        Log.DEBUG -> if (t != null) Log.d(tag, msg, t) else Log.d(tag, msg)
        Log.INFO -> Log.i(tag, msg)
        Log.WARN -> Log.w(tag, msg)
        else -> if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
      }
    }
  }
}

private fun format(level: Int, tag: String, msg: String, t: Throwable?): String {
  val now = System.currentTimeMillis()
  val time = String.format(Locale.ROOT, "%1\$tH:%1\$tM:%1\$tS.%1\$tL", now)
  val throwable = if (t == null) "" else " (${t.javaClass.simpleName}: ${t.message})"
  val levelChar =
      when (level) {
        Log.VERBOSE -> 'V'
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        else -> 'E'
      }
  return "$time [${Thread.currentThread().name}] $levelChar $tag: $msg$throwable"
}
