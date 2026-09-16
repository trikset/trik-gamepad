package com.trikset.gamepad2.diagnostics

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagLevelTest {

  @Test
  fun toBufferLevelMapsEachInput() {
    data class Case(val input: String?, val expected: Long)
    val cases =
        listOf(
            Case(DiagLevel.KEY_ERRORS, Log.WARN.toLong()),
            Case(DiagLevel.KEY_INFO, Log.INFO.toLong()),
            Case(DiagLevel.KEY_DEBUG, Log.DEBUG.toLong()),
            Case(DiagLevel.KEY_VERBOSE, Log.VERBOSE.toLong()),
            Case("garbage", Log.INFO.toLong()),
            Case("", Log.INFO.toLong()),
            Case(null, Log.INFO.toLong()),
        )
    for (case in cases) {
      assertEquals("'${case.input}'", case.expected, DiagLevel.toBufferLevel(case.input).toLong())
    }
  }
}
