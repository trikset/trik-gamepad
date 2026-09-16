package com.trikset.gamepad2.diagnostics

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class AppLogTest {

  @Before
  fun resetBuffer() {
    AppLog.clearForTest()
    AppLog.minBufferLevel = Log.INFO
  }

  @Test
  fun infoWarnAndErrorAreCapturedAtDefaultLevel() {
    AppLog.i("tag", "info line")
    AppLog.w("tag", "warn line")
    AppLog.e("tag", "error line")

    val lines = AppLog.tail(1000)
    assertTrue(lines.any { it.endsWith("I tag: info line") })
    assertTrue(lines.any { it.endsWith("W tag: warn line") })
    assertTrue(lines.any { it.endsWith("E tag: error line") })
  }

  @Test
  fun debugAndVerboseAreFilteredAtDefaultLevel() {
    AppLog.d("tag", "debug line")
    AppLog.v("tag", "verbose line")

    val lines = AppLog.tail(1000)
    assertFalse(lines.any { it.contains("debug line") })
    assertFalse(lines.any { it.contains("verbose line") })
  }

  @Test
  fun errorsOnlyLevelKeepsWarnAndError() {
    AppLog.minBufferLevel = Log.WARN
    AppLog.i("tag", "info line")
    AppLog.d("tag", "debug line")
    AppLog.w("tag", "warn line")
    AppLog.e("tag", "error line")

    val lines = AppLog.tail(1000)
    assertTrue(lines.any { it.endsWith("W tag: warn line") })
    assertTrue(lines.any { it.endsWith("E tag: error line") })
    assertFalse(lines.any { it.contains("info line") })
    assertFalse(lines.any { it.contains("debug line") })
  }

  @Test
  fun debugLevelAddsDebugTrace() {
    AppLog.minBufferLevel = Log.DEBUG
    AppLog.d("tag", "debug line")
    AppLog.i("tag", "info line")

    val lines = AppLog.tail(1000)
    assertTrue(lines.any { it.endsWith("D tag: debug line") })
    assertTrue(lines.any { it.endsWith("I tag: info line") })
  }

  @Test
  fun verboseLevelCapturesEverything() {
    AppLog.minBufferLevel = Log.VERBOSE
    AppLog.v("tag", "verbose line")
    AppLog.d("tag", "debug line")

    val lines = AppLog.tail(1000)
    assertTrue(lines.any { it.endsWith("V tag: verbose line") })
    assertTrue(lines.any { it.endsWith("D tag: debug line") })
  }

  @Test
  fun tailCapsAtRequestedCount() {
    AppLog.i("tag", "a")
    AppLog.i("tag", "b")
    AppLog.i("tag", "c")

    assertEquals(1, AppLog.tail(1).size)
  }

  @Test
  fun formattedLineCarriesTimestampThreadAndLevel() {
    AppLog.i("TagX", "hello world")
    val line = AppLog.tail(1000).first { it.contains("I TagX: hello world") }
    assertTrue(
        "timestamp prefix",
        line.matches(Regex("""\d{2}:\d{2}:\d{2}\.\d{3} \[[^\]]+\] I TagX: hello world""")),
    )
  }

  @Test
  fun errorWithThrowableRecordsClassAndMessage() {
    AppLog.e("tag", "boom", IllegalArgumentException("bad argument"))
    val line = AppLog.tail(1000).first { it.contains("E tag: boom") }
    assertTrue(line.contains("(IllegalArgumentException: bad argument)"))
  }

  @Test
  fun debugLineIsNotEmittedToLogcatByDefault() {
    AppLog.d("defaultNoDebug", "should not reach logcat")
    val records = ShadowLog.getLogsForTag("defaultNoDebug")
    assertTrue("DEBUG lines are gated out when the tag is not set to DEBUG", records.isEmpty())
  }

  @Test
  fun debugLineIsEmittedToLogcatWhenTagLevelIsRaised() {
    ShadowLog.setLoggable("raisedDebug", Log.DEBUG)
    AppLog.d("raisedDebug", "now visible")
    val records = ShadowLog.getLogsForTag("raisedDebug")
    assertEquals(1, records.size)
    assertEquals("now visible", records[0].msg)
  }

  @Test
  fun debugWithThrowableIsBufferedAndLoggedWhenTagRaised() {
    ShadowLog.setLoggable("raisedDebugThrowable", Log.DEBUG)
    AppLog.minBufferLevel = Log.DEBUG
    AppLog.d("raisedDebugThrowable", "recovery", IllegalArgumentException("bad"))

    val line = AppLog.tail(1000).first { it.contains("D raisedDebugThrowable: recovery") }
    assertTrue(line.contains("(IllegalArgumentException: bad)"))
    val records = ShadowLog.getLogsForTag("raisedDebugThrowable")
    assertEquals(1, records.size)
    assertNotNull(records[0].throwable)
  }

  @Test
  fun allLevelsEmitToLogcatWhenTagIsFullyVerbose() {
    ShadowLog.setLoggable("fullVerbose", Log.VERBOSE)
    AppLog.v("fullVerbose", "v")
    AppLog.d("fullVerbose", "d")
    AppLog.i("fullVerbose", "i")
    AppLog.w("fullVerbose", "w")
    AppLog.e("fullVerbose", "e")

    val records = ShadowLog.getLogsForTag("fullVerbose")
    assertEquals(5, records.size)
  }
}
