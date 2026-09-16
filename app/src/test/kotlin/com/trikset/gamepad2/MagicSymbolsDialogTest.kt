package com.trikset.gamepad2

import android.content.SharedPreferences
import android.os.Looper
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

/** Dialog wiring: pre-fill, save persistence, and the "Use default symbols" refill. */
@RunWith(RobolectricTestRunner::class)
class MagicSymbolsDialogTest : RobolectricTestBase() {

  private lateinit var prefs: SharedPreferences
  private lateinit var store: MagicSymbolsStore
  private lateinit var activity: SettingsActivity

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().clear().commit()
    store = MagicSymbolsStore(prefs)
  }

  @Test
  fun showShouldPreFillFieldsWithResolvedSymbols() {
    MagicSymbolsDialog.show(activity, store) {}
    val fields = editFields(latestDialog())
    assertEquals(MagicSymbolsStore.MAX_BUTTONS, fields.size)
    assertEquals("▲", fields[0].text.toString())
    assertEquals("◆", fields[4].text.toString())
  }

  @Test
  fun showShouldPreFillStoredSymbolsWhenSet() {
    prefs.edit().putString(SettingsFragment.magicSymbolKey(1), "A").commit()
    MagicSymbolsDialog.show(activity, store) {}
    val fields = editFields(latestDialog())
    assertEquals("A", fields[0].text.toString())
  }

  @Test
  fun saveShouldPersistEditedSymbols() {
    MagicSymbolsDialog.show(activity, store) {}
    val dialog = latestDialog()
    val fields = editFields(dialog)
    fields[0].setText("A")
    clickAndIdle(dialog.getButton(AlertDialog.BUTTON_POSITIVE))

    assertEquals("A", prefs.getString(SettingsFragment.magicSymbolKey(1), ""))
    // Untouched slots keep their resolved (default) values.
    assertEquals("◆", prefs.getString(SettingsFragment.magicSymbolKey(5), ""))
  }

  @Test
  fun saveShouldInvokeOnSavedCallback() {
    var saved = false
    MagicSymbolsDialog.show(activity, store) { saved = true }
    val dialog = latestDialog()
    clickAndIdle(dialog.getButton(AlertDialog.BUTTON_POSITIVE))
    assertNotNull("onSaved must fire after Save", if (saved) Any() else null)
  }

  @Test
  fun useDefaultSymbolsShouldRefillFieldsWithoutDismissing() {
    prefs.edit().putString(SettingsFragment.magicSymbolKey(1), "A").commit()
    MagicSymbolsDialog.show(activity, store) {}
    val dialog = latestDialog()
    val fields = editFields(dialog)
    assertEquals("A", fields[0].text.toString())

    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()

    assertEquals("▲", fields[0].text.toString())
    assertEquals("◆", fields[4].text.toString())
    assertEquals("dialog must stay open after refilling", false, !dialog.isShowing)
  }

  private fun latestDialog(): AlertDialog {
    val dialog = ShadowDialog.getLatestDialog() as AlertDialog
    // dialog.show() populates the window via the main looper; without idling the fields/buttons
    // below are not attached yet.
    org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
    return dialog
  }

  private fun clickAndIdle(button: android.view.View?) {
    button?.performClick()
    // The appcompat button dispatches through its Handler; idle so the listener + pref apply run.
    org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
  }

  private fun editFields(dialog: AlertDialog): List<EditText> {
    val decor = dialog.window?.decorView ?: return emptyList()
    val fields = ArrayList<EditText>()
    fun collect(view: android.view.View) {
      if (view is EditText) fields.add(view)
      if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
    }
    collect(decor)
    return fields
  }
}
