package com.trikset.gamepad2

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MagicButtonSymbolsTest : RobolectricTestBase() {

  @Test
  fun defaultAndResolveMapsEachInput() {
    data class DefaultCase(val n: Int, val expected: String)
    val defaultCases =
        listOf(
            DefaultCase(1, "▲"),
            DefaultCase(2, "■"),
            DefaultCase(3, "●"),
            DefaultCase(4, "✕"),
            DefaultCase(5, "◆"),
        )
    for (case in defaultCases) {
      assertEquals("default(${case.n})", case.expected, MagicButtonSymbols.default(case.n))
    }
    data class ResolveCase(val n: Int, val stored: String?, val expected: String)
    val resolveCases =
        listOf(
            ResolveCase(1, null, "▲"),
            ResolveCase(1, "  ", "▲"),
            ResolveCase(1, "A", "A"),
            ResolveCase(4, "★", "★"),
        )
    for (case in resolveCases) {
      assertEquals(
          "resolve(${case.n}, '${case.stored}')",
          case.expected,
          MagicButtonSymbols.resolve(case.n, case.stored),
      )
    }
  }
}
