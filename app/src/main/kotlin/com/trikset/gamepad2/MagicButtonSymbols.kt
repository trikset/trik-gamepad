package com.trikset.gamepad2

/**
 * Resolves the display glyph for a magic button. The glyph is purely cosmetic — the protocol stays
 * numeric (`btn N down`) and accessibility uses "Button N"; the defaults are the PlayStation-style
 * geometric figures (▲ ■ ● ✕ ◆), the only culture-neutral gamepad face-button convention. A blank
 * stored symbol falls back to the default.
 */
object MagicButtonSymbols {

  private val DEFAULTS = listOf("▲", "■", "●", "✕", "◆")

  fun default(buttonNumber: Int): String = DEFAULTS[buttonNumber - 1]

  fun resolve(buttonNumber: Int, stored: String?): String =
      stored?.takeIf { it.isNotBlank() } ?: default(buttonNumber)
}
