package com.trikset.gamepad2

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest : RobolectricTestBase() {

  private lateinit var activity: SettingsActivity
  private lateinit var fragment: SettingsFragment

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    fragment =
        activity.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
  }

  @Test
  fun onCreateShouldAddSettingsFragment() {
    assertNotNull(fragment)
  }

  @Test
  fun settingsFragmentShouldLoadAppPreferences() {
    // The fragment reads the shared preferences when it builds its summaries;
    // verify the app-screen preferences were registered by accessing them.
    assertTrue(PreferenceManager.getDefaultSharedPreferences(activity).contains("keepScreenOn"))
  }

  @Test
  fun aboutSystemClickShouldCopyToClipboard() {
    val about = fragment.findPreference<Preference>(SettingsFragment.SK_ABOUT_SYSTEM)
    assertNotNull(about)
    // Trigger the click listener set up by the fragment.
    about!!.onPreferenceClickListener!!.onPreferenceClick(about)

    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val primary = clipboard.primaryClip
    assertNotNull(primary)
    assertTrue(primary!!.getItemAt(0).text.length > 0)
  }

  @Test
  fun aboutSystemClickWithoutActivityShouldThrow() {
    val fragment = SettingsFragment()
    val method = fragment.javaClass.getDeclaredMethod("initializeAboutSystemField")
    method.isAccessible = true
    assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
      method.invoke(fragment)
    }
  }

  @Test
  fun onPreferenceStartScreenShouldPushNestedFragment() {
    val manager = PreferenceManager(activity)
    val screen = manager.createPreferenceScreen(activity)
    screen.key = SettingsFragment.SK_ADVANCED
    assertTrue(activity.onPreferenceStartScreen(fragment, screen))
    activity.supportFragmentManager.executePendingTransactions()
    val top = activity.supportFragmentManager.fragments.last()
    assertTrue("the nested screen must push a new SettingsFragment", top is SettingsFragment)
    assertEquals(
        SettingsFragment.SK_ADVANCED,
        top.arguments?.getString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT),
    )
  }

  @Test
  fun onPreferenceStartScreenWithAppXmlArgShouldPreservePreferenceXml() {
    // When the caller fragment has ARG_PREFERENCE_XML set, the nested screen carries it forward.
    val manager = PreferenceManager(activity)
    val screen = manager.createPreferenceScreen(activity)
    screen.key = SettingsFragment.SK_ADVANCED
    val caller = SettingsFragment()
    caller.arguments =
        Bundle().apply {
          putInt(SettingsFragment.ARG_PREFERENCE_XML, R.xml.pref_app)
        }
    activity.supportFragmentManager.beginTransaction().add(caller, "caller").commitNow()
    assertTrue(activity.onPreferenceStartScreen(caller, screen))
    activity.supportFragmentManager.executePendingTransactions()
    val top = activity.supportFragmentManager.fragments.last()
    assertEquals(
        "preference XML must be preserved",
        R.xml.pref_app,
        top.arguments?.getInt(SettingsFragment.ARG_PREFERENCE_XML, 0),
    )
  }

  @Test
  fun nestedFragmentWithAdvancedRootShouldLoadAdvancedPrefs() {
    // The sub-screen fragment runs onCreatePreferences with rootKey="advancedSettings", so its
    // tree is the Advanced subtree and the init helpers resolve within it.
    val sub = SettingsFragment()
    val args = Bundle()
    args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, SettingsFragment.SK_ADVANCED)
    sub.arguments = args
    activity.supportFragmentManager.beginTransaction().add(sub, "advanced-sub").commitNow()

    assertNotNull(
        "About must resolve inside the Advanced subtree",
        sub.findPreference<Preference>(SettingsFragment.SK_ABOUT_SYSTEM),
    )
    assertNotNull(
        "magic-button count must resolve inside the Advanced subtree",
        sub.findPreference<Preference>(SettingsFragment.SK_MAGIC_BUTTON_COUNT),
    )
  }

  @Test
  fun openRobotSettingsRowShouldLaunchRobotSettings() {
    val row = fragment.findPreference<Preference>(SettingsFragment.SK_OPEN_ROBOT_SETTINGS)
    assertNotNull("the cross-link row must exist in the app screen", row)
    row!!.onPreferenceClickListener!!.onPreferenceClick(row)
    val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
    assertEquals(RobotSettingsActivity::class.java.name, intent?.component?.className)
  }

  @Test
  fun magicSymbolsRowShouldShowResolvedGlyphSummary() {
    val symbols = fragment.findPreference<Preference>(SettingsFragment.SK_MAGIC_SYMBOLS)
    assertNotNull(symbols)
    assertEquals("▲ ■ ● ✕ ◆", symbols!!.summary)
  }

  @Test
  fun magicSymbolsClickShouldOpenSymbolsDialog() {
    val symbols = fragment.findPreference<Preference>(SettingsFragment.SK_MAGIC_SYMBOLS)
    assertNotNull(symbols)
    symbols!!.onPreferenceClickListener!!.onPreferenceClick(symbols)
    assertNotNull(
        "tapping Button symbols must open the glyph dialog",
        org.robolectric.shadows.ShadowDialog.getLatestDialog(),
    )
  }

  @Test
  fun diagLevelSummaryShouldShowCurrentLabelOnChange() {
    val diag = fragment.findPreference<Preference>(SettingsFragment.SK_DIAG_LEVEL)
    assertNotNull(diag)
    diag!!.onPreferenceChangeListener!!.onPreferenceChange(diag, "verbose")
    assertTrue((diag.summary ?: "").toString().contains("Verbose"))
    assertTrue((diag.summary ?: "").toString().contains("How much detail"))
  }

  @Test
  fun seekBarSummariesShouldShowValueAndDescription() {
    val wheel = fragment.findPreference<Preference>(SettingsFragment.SK_WHEEL_STEP)
    assertNotNull(wheel)
    wheel!!.onPreferenceChangeListener!!.onPreferenceChange(wheel, 12)
    assertEquals("12 · Smaller = more sensitive; 5..10 is typical", wheel.summary)
  }

  @Test
  fun magicSizeSeekBarShouldShowValue() {
    val size = fragment.findPreference<Preference>(SettingsFragment.SK_MAGIC_BUTTON_SIZE)
    assertNotNull(size)
    assertEquals("100", size!!.summary)
    size.onPreferenceChangeListener!!.onPreferenceChange(size, 130)
    assertEquals("130", size.summary)
  }

  @Test
  fun seekBarSummariesShowDefaultsWhenUnset() {
    // Fresh install: no stored values -> the XML defaults (7/100/3/100) must show, not a fabricated
    // 0.
    val wheel = fragment.findPreference<Preference>(SettingsFragment.SK_WHEEL_STEP)
    assertEquals("7 · Smaller = more sensitive; 5..10 is typical", wheel!!.summary)
    val pads = fragment.findPreference<Preference>(SettingsFragment.SK_SHOW_PADS)
    assertEquals("100 · 0 for fully transparent, 255 is opaque", pads!!.summary)
    val count = fragment.findPreference<Preference>(SettingsFragment.SK_MAGIC_BUTTON_COUNT)
    assertEquals("3 · 0 hides the row; 1..5 buttons", count!!.summary)
    val size = fragment.findPreference<Preference>(SettingsFragment.SK_MAGIC_BUTTON_SIZE)
    assertEquals("100", size!!.summary)
  }

  @Test
  fun seekBarSummariesHonorLegacyStringStorage() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_WHEEL_STEP, "12").commit()
    // Direct call: the fragment's own initialization cannot reach the String branch without the
    // SeekBarPreference view crashing first (prefs.getInt on a String value throws), but the
    // defensive parser must still honor it (the same shared helper MainActivitySettingsController
    // uses for the live pads-alpha/wheel-step settings).
    assertEquals(
        12,
        SettingsFragment.readSeekBarValue(prefs, SettingsFragment.SK_WHEEL_STEP, 7),
    )
    assertEquals(
        "unparseable String falls back to the default",
        7,
        SettingsFragment.readSeekBarValue(prefs, SettingsFragment.SK_WHEEL_STEP + ".nope", 7),
    )
  }

  @Test
  fun magicSizeReadSeekBarHonorsIntAndString() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    assertEquals(
        100,
        SettingsFragment.readSeekBarValue(prefs, SettingsFragment.SK_MAGIC_BUTTON_SIZE, 100),
    )
    prefs.edit().putInt(SettingsFragment.SK_MAGIC_BUTTON_SIZE, 120).commit()
    assertEquals(
        120,
        SettingsFragment.readSeekBarValue(prefs, SettingsFragment.SK_MAGIC_BUTTON_SIZE, 100),
    )
    prefs.edit().putString(SettingsFragment.SK_MAGIC_BUTTON_SIZE, "80").commit()
    assertEquals(
        80,
        SettingsFragment.readSeekBarValue(prefs, SettingsFragment.SK_MAGIC_BUTTON_SIZE, 100),
    )
  }

  @Test
  fun diagLevelSummaryFallsBackToInfoForUnknownValue() {
    val diag = fragment.findPreference<Preference>(SettingsFragment.SK_DIAG_LEVEL)
    assertNotNull(diag)
    diag!!.onPreferenceChangeListener!!.onPreferenceChange(diag, "bogus")
    assertTrue((diag.summary ?: "").toString().contains("Info"))
  }

  @Test
  fun copyReportClickShouldCopyFullReport() {
    val copy = fragment.findPreference<Preference>(SettingsFragment.SK_COPY_REPORT)
    assertNotNull(copy)
    copy!!.onPreferenceClickListener!!.onPreferenceClick(copy)
    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = clipboard.primaryClip!!.getItemAt(0).text.toString()
    assertTrue("the copied report must include the version header", text.contains("Version:"))
  }

  @Test
  fun viewLogClickShouldShowDialog() {
    val viewLog = fragment.findPreference<Preference>(SettingsFragment.SK_VIEW_LOG)
    assertNotNull(viewLog)
    viewLog!!.onPreferenceClickListener!!.onPreferenceClick(viewLog)
    assertNotNull(
        "view log must open a dialog",
        org.robolectric.shadows.ShadowDialog.getLatestDialog(),
    )
  }

  @Test
  fun viewLogClickShouldIncludeLogTail() {
    com.trikset.gamepad2.diagnostics.AppLog.clearForTest()
    com.trikset.gamepad2.diagnostics.AppLog.i("test", "hello-log-line")
    val viewLog = fragment.findPreference<Preference>(SettingsFragment.SK_VIEW_LOG)
    viewLog!!.onPreferenceClickListener!!.onPreferenceClick(viewLog)
    val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
    val message = dialog!!.findViewById<android.widget.TextView>(android.R.id.message)
    assertTrue((message.text.toString()).contains("hello-log-line"))
  }

  @Test
  fun magicSymbolsDialogSaveShouldRefreshRowSummary() {
    val symbols = fragment.findPreference<Preference>(SettingsFragment.SK_MAGIC_SYMBOLS)!!
    symbols.onPreferenceClickListener!!.onPreferenceClick(symbols)
    val dialog =
        org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    val fields = dialogViews(dialog) { it is android.widget.EditText }
    assertTrue("the dialog must pre-fill 5 glyph fields", fields.size == 5)
    (fields[0] as android.widget.EditText).setText("Z")
    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    assertTrue(
        "row summary must reflect the saved glyph",
        symbols.summary.toString().startsWith("Z"),
    )
  }
}
