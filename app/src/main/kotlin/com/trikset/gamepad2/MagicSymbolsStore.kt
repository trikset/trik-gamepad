package com.trikset.gamepad2

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persists the per-button display glyphs for the magic buttons. Storage stays under the legacy
 * `magicSymbol1..5` keys, which are **independent** of `magicButtonCount` (the count only gates how
 * many buttons are rendered; editing symbols never touches it). An unset/blank slot resolves to the
 * culture-neutral default (▲ ■ ● ✕ ◆).
 */
class MagicSymbolsStore(private val prefs: SharedPreferences) {

  fun read(buttonNumber: Int): String =
      MagicButtonSymbols.resolve(
          buttonNumber,
          prefs.getString(SettingsFragment.magicSymbolKey(buttonNumber), null),
      )

  fun readAll(): List<String> = (1..MAX_BUTTONS).map(::read)

  fun defaults(): List<String> = (1..MAX_BUTTONS).map(MagicButtonSymbols::default)

  fun save(symbols: List<String>) {
    prefs.edit {
      for (n in 1..MAX_BUTTONS) {
        putString(SettingsFragment.magicSymbolKey(n), symbols.getOrNull(n - 1))
      }
    }
  }

  companion object {
    const val MAX_BUTTONS = SettingsFragment.MAX_MAGIC_BUTTONS
  }
}
