package com.trikset.gamepad2

import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.trikset.gamepad2.glyphs.GlyphRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Direct tests for the robot/target settings screen ([RobotSettingsActivity] + pref_robot.xml):
 * host/port/keepalive summaries, video-URI reset, copy-IP and the presets category.
 */
@RunWith(RobolectricTestRunner::class)
class RobotSettingsActivityTest : RobolectricTestBase() {

  private lateinit var activity: RobotSettingsActivity
  private lateinit var fragment: SettingsFragment

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(RobotSettingsActivity::class.java).setup().get()
    fragment =
        activity.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
  }

  @Test
  fun onCreateShouldAddSettingsFragment() {
    assertNotNull(fragment)
  }

  @Test
  fun settingsFragmentShouldLoadRobotPreferences() {
    assertTrue(PreferenceManager.getDefaultSharedPreferences(activity).contains("hostAddress"))
  }

  @Test
  fun dynamicSummaryShouldUpdateOnChange() {
    val host = fragment.findPreference<Preference>(SettingsFragment.SK_HOST_ADDRESS)
    assertNotNull(host)
    // The change listener sets the summary to the new value.
    host!!.onPreferenceChangeListener!!.onPreferenceChange(host, "10.0.0.9")
    assertTrue((host.summary ?: "").toString().contains("10.0.0.9"))
  }

  @Test
  fun hostPortAndKeepaliveSummariesShouldUpdateOnChange() {
    for (key in arrayOf(SettingsFragment.SK_HOST_PORT, SettingsFragment.SK_KEEPALIVE)) {
      val pref = fragment.findPreference<Preference>(key)
      assertNotNull(pref)
      pref!!.onPreferenceChangeListener!!.onPreferenceChange(pref, "7777")
      assertTrue((pref.summary ?: "").toString().contains("7777"))
    }
  }

  @Test
  fun keepaliveRowShouldRejectInvalidInputAtEditTime() {
    // The keepalive row must reject out-of-range input at edit time (return false, keep the stored
    // summary) — otherwise the row summary desyncs from the stored value the controller enforces.
    for (badInput in arrayOf<Any>(SenderService.MINIMAL_KEEPALIVE - 1, "abc")) {
      val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
      prefs.edit().putString(SettingsFragment.SK_KEEPALIVE, "5000").commit()
      val rebuilt = buildFragment()
      val keepalive = rebuilt.findPreference<Preference>(SettingsFragment.SK_KEEPALIVE)
      assertNotNull("keepalive row must exist in the robot screen", keepalive)

      val accepted =
          keepalive!!.onPreferenceChangeListener!!.onPreferenceChange(keepalive, badInput)
      assertFalse("an out-of-range or non-numeric keepalive must be rejected", accepted)
      assertEquals(
          "the summary must keep the stored value after a rejected edit",
          "5000",
          keepalive.summary,
      )
    }
  }

  @Test
  fun videoChipCamera1FillsUriFromHostWhenEmpty() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    prefs.edit().putString(SettingsFragment.SK_VIDEO_URI, "").commit()
    tapChip(VideoSourceChip.CAMERA_1)

    assertEquals(
        "http://10.0.0.9:8080/?action=stream",
        prefs.getString(SettingsFragment.SK_VIDEO_URI, ""),
    )
    // The videoURI row shows the chip result in its summary.
    val videoUri = fragment.findPreference<Preference>(SettingsFragment.SK_VIDEO_URI)
    assertNotNull(videoUri)
    assertTrue((videoUri!!.summary ?: "").toString().contains("10.0.0.9:8080"))
  }

  @Test
  fun videoChipRewritesOnlyPortOfStoredUri() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    prefs
        .edit()
        .putString(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.9:8081/stream?low=1")
        .commit()
    tapChip(VideoSourceChip.CAMERA_1)

    assertEquals(
        "http://10.0.0.9:8080/stream?low=1",
        prefs.getString(SettingsFragment.SK_VIDEO_URI, ""),
    )
  }

  @Test
  fun videoChipUsbKeepsHostWhenStoredUriExists() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "192.168.1.9").commit()
    prefs
        .edit()
        .putString(SettingsFragment.SK_VIDEO_URI, "http://192.168.1.9:8080/?action=stream")
        .commit()
    tapChip(VideoSourceChip.USB)

    assertEquals(
        "http://192.168.1.9:8082/?action=stream",
        prefs.getString(SettingsFragment.SK_VIDEO_URI, ""),
    )
  }

  @Test
  fun videoChipNoneClearsUri() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    prefs
        .edit()
        .putString(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.9:8080/?action=stream")
        .commit()
    tapChip(VideoSourceChip.NONE)

    assertEquals("", prefs.getString(SettingsFragment.SK_VIDEO_URI, ""))
  }

  @Test
  fun videoChipWithNoStoredUriAndNoHostShowsHintAndChangesNothing() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "").commit()
    prefs.edit().putString(SettingsFragment.SK_VIDEO_URI, "").commit()
    val rebuilt = buildFragment()
    val chips = rebuilt.findPreference<VideoSourceChipsPreference>(SettingsFragment.SK_VIDEO_CHIPS)
    assertNotNull(chips)
    chips!!.onChipSelected!!.invoke(VideoSourceChip.CAMERA_2)

    // Nothing was written and the row still shows the empty state.
    assertEquals("", prefs.getString(SettingsFragment.SK_VIDEO_URI, ""))
    val videoUri = rebuilt.findPreference<Preference>(SettingsFragment.SK_VIDEO_URI)
    assertNotNull(videoUri)
    assertEquals("No stream URI set", videoUri!!.summary)
  }

  private fun tapChip(chip: VideoSourceChip) {
    val chips = fragment.findPreference<VideoSourceChipsPreference>(SettingsFragment.SK_VIDEO_CHIPS)
    assertNotNull("video-source chips row must exist in the robot screen", chips)
    chips!!.onChipSelected!!.invoke(chip)
  }

  @Test
  fun copyRobotIpClickShouldCopyHostToClipboard() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    val copy = fragment.findPreference<Preference>(SettingsFragment.SK_COPY_ROBOT_IP)
    assertNotNull(copy)
    copy!!.onPreferenceClickListener!!.onPreferenceClick(copy)

    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val primary = clipboard.primaryClip
    assertNotNull(primary)
    assertEquals("10.0.0.9", primary!!.getItemAt(0).text.toString())
  }

  @Test
  fun videoChipsWithoutActivityShouldBeSafe() {
    val fragment = SettingsFragment()
    val method = fragment.javaClass.getDeclaredMethod("initializeVideoSourceChipsField")
    method.isAccessible = true
    method.invoke(fragment)
  }

  @Test
  fun copyRobotIpClickWithoutActivityShouldThrow() {
    val fragment = SettingsFragment()
    val method = fragment.javaClass.getDeclaredMethod("initializeCopyRobotIpField")
    method.isAccessible = true
    assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
      method.invoke(fragment)
    }
  }

  @Test
  fun videoUriSummaryShouldShowEmptyStateLabelWhenExplicitlyEmpty() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_VIDEO_URI, "").commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "").commit()
    // Rebuild so the summary reflects the (empty) prefs at setup time.
    val rebuilt = buildFragment()
    val videoUri = rebuilt.findPreference<Preference>(SettingsFragment.SK_VIDEO_URI)
    assertNotNull(videoUri)
    // An explicitly-empty URI (with no host to derive from) is a genuine "video disabled" state.
    assertEquals("No stream URI set", videoUri!!.summary)
  }

  @Test
  fun videoUriSummaryShouldShowDerivedUrlWhenUnsetButHostConfigured() {
    // An unset URI with a configured host: the app streams the host-derived default, so the row
    // must show that URL (never "No stream URI set" while the app actually streams).
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().remove(SettingsFragment.SK_VIDEO_URI).commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    val rebuilt = buildFragment()
    val videoUri = rebuilt.findPreference<Preference>(SettingsFragment.SK_VIDEO_URI)
    assertNotNull(videoUri)
    assertEquals("http://10.0.0.9:8080/?action=stream", videoUri!!.summary)
  }

  @Test
  fun videoUriSummaryShouldRefreshOnHostChange() {
    // The derived video-URI summary follows the host: editing the host updates the row.
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().remove(SettingsFragment.SK_VIDEO_URI).commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    val rebuilt = buildFragment()
    val host = rebuilt.findPreference<Preference>(SettingsFragment.SK_HOST_ADDRESS)
    assertNotNull(host)
    host!!.onPreferenceChangeListener!!.onPreferenceChange(host, "10.0.0.7")

    val videoUri = rebuilt.findPreference<Preference>(SettingsFragment.SK_VIDEO_URI)
    assertNotNull(videoUri)
    assertEquals("http://10.0.0.7:8080/?action=stream", videoUri!!.summary)
  }

  /** Builds a fresh [RobotSettingsActivity] + fragment (prefs are read at setup). */
  private fun buildFragment(): SettingsFragment {
    val rebuilt = Robolectric.buildActivity(RobotSettingsActivity::class.java).setup().get()
    return rebuilt.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
  }

  @Test
  fun chipsRowShouldRepopulateWhenRecenterPrefChanges() {
    // The chips' glyph alignment follows the global "Smart glyph alignment" pref, which lives on
    // the
    // App-settings screen. That screen can sit on top of Robot settings in the back stack, so the
    // chips row must re-render itself when the pref flips (a settings RecyclerView does not re-bind
    // rows on resume).
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putBoolean(SettingsFragment.SK_RECENTER_GLYPHS, false).commit()
    val chips = fragment.findPreference<VideoSourceChipsPreference>(SettingsFragment.SK_VIDEO_CHIPS)
    assertNotNull("video-source chips row must exist in the robot screen", chips)
    chips!!.onAttached()
    try {
      val row = bindChipsRow(chips)
      val cellBefore = row.getChildAt(0)
      assertTrue(
          "the chips row must render four cells",
          row.childCount == VideoSourceChip.entries.size,
      )
      assertEquals(
          "recenter must never translate a chip",
          0f,
          (cellBefore as Button).translationX,
          0f,
      )

      prefs.edit().putBoolean(SettingsFragment.SK_RECENTER_GLYPHS, true).commit()
      idleLooper()

      val cellAfter = row.getChildAt(0)
      assertNotSame(
          "flipping the recenter pref must repopulate the bound chips row",
          cellBefore,
          cellAfter,
      )
      assertEquals(
          "the repopulated row must keep the four chips",
          VideoSourceChip.entries.size,
          row.childCount,
      )
      assertEquals(
          "recenter must never translate a chip after a re-render",
          0f,
          (cellAfter as Button).translationX,
          0f,
      )
    } finally {
      chips.onDetached()
    }
  }

  @Test
  fun chipsRowShouldDefaultToSmartGlyphAlignment() {
    // "Smart glyph alignment" ships ON (ink-box centering): a fresh install with no stored pref
    // must render exactly as an explicit ON — a regression guard against the fallback silently
    // flipping the shipped look back to the baseline mode.
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().remove(SettingsFragment.SK_RECENTER_GLYPHS).commit()
    val chips = fragment.findPreference<VideoSourceChipsPreference>(SettingsFragment.SK_VIDEO_CHIPS)
    assertNotNull("video-source chips row must exist in the robot screen", chips)
    chips!!.onAttached()
    try {
      val row = bindChipsRow(chips)
      assertTrue(
          "the chips row must carry vertical breathing room so rings clear the list divider",
          row.paddingTop > 0 && row.paddingBottom == row.paddingTop,
      )
      val padsAtDefault = chipsPadding(row)
      prefs.edit().putBoolean(SettingsFragment.SK_RECENTER_GLYPHS, true).commit()
      idleLooper()
      val padsAtOn = chipsPadding(row)
      assertEquals(
          "an unset pref must render the ON (ink-box) alignment, not the OFF mode",
          padsAtOn.map { it.toList() },
          padsAtDefault.map { it.toList() },
      )
      prefs.edit().putBoolean(SettingsFragment.SK_RECENTER_GLYPHS, false).commit()
      idleLooper()
      // OFF = the plain baseline mode: no ink correction, so every cell keeps zero asymmetric
      // padding (default gravity centering only).
      val padsAtOff = chipsPadding(row)
      assertTrue(
          "the OFF baseline mode must not pad any cell (got ${
              padsAtOff.map { it.toList() }
          })",
          padsAtOff.all { it.all { p -> p == 0 } },
      )
    } finally {
      chips.onDetached()
    }
  }

  /**
   * Inflates + binds the chips row against [chips] (see
   * chipsRowShouldRepopulateWhenRecenterPrefChanges).
   */
  private fun bindChipsRow(chips: VideoSourceChipsPreference): GlyphRow {
    val row =
        LayoutInflater.from(activity).inflate(R.layout.pref_video_source_chips, null) as GlyphRow
    chips.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row))
    return row
  }

  private fun idleLooper() {
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
  }

  /** Per-cell asymmetric paddings ([paddingStart, paddingTop, paddingEnd, paddingBottom]). */
  private fun chipsPadding(row: GlyphRow): List<IntArray> =
      (0 until row.childCount).map {
        val b = row.getChildAt(it) as Button
        intArrayOf(b.paddingStart, b.paddingTop, b.paddingEnd, b.paddingBottom)
      }

  private fun presetRows(): List<Preference> {
    val category = fragment.findPreference<PreferenceCategory>(SettingsFragment.SK_ROBOT_PRESETS)
    assertNotNull("robot presets category must exist in the robot screen", category)
    return (0 until category!!.preferenceCount).map { category.getPreference(it) }
  }

  @Test
  fun savePresetShouldStoreAndRefreshRows() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_PORT, "4444").commit()
    val save = fragment.findPreference<EditTextPreference>(SettingsFragment.SK_SAVE_PRESET)
    assertNotNull(save)
    assertTrue(save!!.onPreferenceChangeListener!!.onPreferenceChange(save, "workshop"))

    assertTrue(
        "a dynamic row named 'workshop' must appear",
        presetRows().any { it.title.toString() == "workshop" },
    )
    assertEquals("4444", RobotPresetStore(prefs).all()["workshop"]?.port)
  }

  @Test
  fun savePresetWithEmptyNameShouldReject() {
    val save = fragment.findPreference<EditTextPreference>(SettingsFragment.SK_SAVE_PRESET)
    assertNotNull(save)
    val accepted = save!!.onPreferenceChangeListener!!.onPreferenceChange(save, "   ")
    assertFalse("blank names must be rejected", accepted)
    assertTrue(presetRows().none { it.title.toString() == "" })
  }

  @Test
  fun applyPresetShouldWriteHostPortAndUri() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_PORT, "4444").commit()
    prefs
        .edit()
        .putString(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.9:8080/?action=stream")
        .commit()
    val save = fragment.findPreference<EditTextPreference>(SettingsFragment.SK_SAVE_PRESET)!!
    save.onPreferenceChangeListener!!.onPreferenceChange(save, "workshop")

    // Change the live settings, then apply the preset back.
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "0.0.0.0").commit()
    val row = presetRows().first { it.title.toString() == "workshop" }
    row.onPreferenceClickListener!!.onPreferenceClick(row)

    assertEquals("10.0.0.9", prefs.getString(SettingsFragment.SK_HOST_ADDRESS, ""))
    assertEquals("4444", prefs.getString(SettingsFragment.SK_HOST_PORT, ""))
    assertEquals(
        "http://10.0.0.9:8080/?action=stream",
        prefs.getString(SettingsFragment.SK_VIDEO_URI, ""),
    )
  }

  @Test
  fun deletePresetWithNoPresetsShouldBeSafe() {
    val delete = fragment.findPreference<Preference>(SettingsFragment.SK_DELETE_PRESET)
    assertNotNull(delete)
    // With no presets saved the delete tap shows a toast and does not open a dialog.
    delete!!.onPreferenceClickListener!!.onPreferenceClick(delete)
  }

  @Test
  fun deletePresetWithExistingPresetsShouldShowDialogAndDeleteOnItemTap() {
    seedHostAndPort()
    val save = fragment.findPreference<EditTextPreference>(SettingsFragment.SK_SAVE_PRESET)!!
    save.onPreferenceChangeListener!!.onPreferenceChange(save, "workshop")

    val delete = fragment.findPreference<Preference>(SettingsFragment.SK_DELETE_PRESET)!!
    delete.onPreferenceClickListener!!.onPreferenceClick(delete)
    val dialog =
        org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    val list =
        dialogViews(dialog) { it is android.widget.ListView }.first() as android.widget.ListView
    list.performItemClick(list.adapter.getView(0, null, list), 0, list.adapter.getItemId(0))

    assertTrue(
        "the preset must be deleted after tapping its row in the dialog",
        RobotPresetStore(PreferenceManager.getDefaultSharedPreferences(activity)).all().isEmpty(),
    )
  }

  @Test
  fun openAppSettingsRowShouldLaunchAppSettings() {
    val row = fragment.findPreference<Preference>(SettingsFragment.SK_OPEN_APP_SETTINGS)
    assertNotNull("the cross-link row must exist in the robot screen", row)
    row!!.onPreferenceClickListener!!.onPreferenceClick(row)
    val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
    assertEquals(SettingsActivity::class.java.name, intent?.component?.className)
  }

  /** Seeds the robot host/port prefs used by the preset save/delete tests. */
  private fun seedHostAndPort(host: String = "10.0.0.9", port: String = "4444") {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, host).commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_PORT, port).commit()
  }
}
