package com.trikset.gamepad2

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Direct tests for [MagicSymbolsStore]: pre-fill defaults, resolve stored values, save, reset. */
@RunWith(RobolectricTestRunner::class)
class MagicSymbolsStoreTest : RobolectricTestBase() {

  private lateinit var prefs: SharedPreferences
  private lateinit var store: MagicSymbolsStore

  @Before
  fun setUp() {
    prefs =
        PreferenceManager.getDefaultSharedPreferences(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
    prefs.edit().clear().commit()
    store = MagicSymbolsStore(prefs)
  }

  @Test
  fun readShouldFallBackToDefaultsWhenUnset() {
    assertEquals("▲", store.read(1))
    assertEquals("◆", store.read(5))
  }

  @Test
  fun readShouldResolveStoredValues() {
    prefs.edit().putString(SettingsFragment.magicSymbolKey(1), "A").commit()
    assertEquals("A", store.read(1))
  }

  @Test
  fun readAllShouldReturnResolvedGlyphsInOrder() {
    prefs.edit().putString(SettingsFragment.magicSymbolKey(2), "X").commit()
    assertEquals(listOf("▲", "X", "●", "✕", "◆"), store.readAll())
  }

  @Test
  fun defaultsShouldReturnCultureNeutralGlyphs() {
    assertEquals(listOf("▲", "■", "●", "✕", "◆"), store.defaults())
  }

  @Test
  fun saveShouldPersistTheWholeArray() {
    store.save(listOf("A", "B", "C", "D", "E"))
    assertEquals("A", prefs.getString(SettingsFragment.magicSymbolKey(1), ""))
    assertEquals("E", prefs.getString(SettingsFragment.magicSymbolKey(5), ""))
    assertEquals("A", store.read(1))
  }

  @Test
  fun saveShouldNeverTouchButtonCount() {
    prefs.edit().putInt(SettingsFragment.SK_MAGIC_BUTTON_COUNT, 2).commit()
    store.save(listOf("A", "B", "C", "D", "E"))
    assertEquals(
        "symbol save must leave the display count untouched",
        2,
        prefs.getInt(SettingsFragment.SK_MAGIC_BUTTON_COUNT, -1),
    )
  }
}
