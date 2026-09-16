package com.trikset.gamepad2

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.diagnostics.AppLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.util.concurrent.PausedExecutorService

/** Direct tests for [MainActivitySettingsController]. */
@RunWith(RobolectricTestRunner::class)
class MainActivitySettingsControllerTest : RobolectricTestBase() {

  private class FakeUi : MainActivitySettingsController.SettingsUi {
    var chipText: String? = null
    var toasts = mutableListOf<String>()
    var url: String? = null
    var lastAlpha = 0f
    var previousAlpha = 0f

    override fun setTargetChip(host: String) {
      this.chipText = host
    }

    override fun toast(text: String) {
      toasts.add(text)
    }

    override fun animatePadsAlpha(alpha: Float, previousAlpha: Float) {
      lastAlpha = alpha
      this.previousAlpha = previousAlpha
    }

    override fun setVideoUrl(url: String?) {
      this.url = url
    }

    override var wheelStep: Int = 7

    override var wheelEnabled: Boolean = false

    var keepScreenOnState = true

    override fun setKeepScreenOn(enabled: Boolean) {
      keepScreenOnState = enabled
    }

    var magicCount = -1
    var magicSymbols = listOf<String>()
    var magicSizePercent = 100
    var controlsVisibleState = true
    var showFpsState = false

    override fun setMagicButtons(
        count: Int,
        symbols: List<String>,
        sizePercent: Int,
    ) {
      magicCount = count
      magicSymbols = symbols
      magicSizePercent = sizePercent
    }

    override fun setControlsVisible(visible: Boolean) {
      controlsVisibleState = visible
    }

    override fun setShowFps(enabled: Boolean) {
      showFpsState = enabled
    }

    var cropToFillState = false

    override fun setVideoCropToFill(enabled: Boolean) {
      cropToFillState = enabled
    }
  }

  private lateinit var context: Context
  private lateinit var prefs: SharedPreferences
  private lateinit var sender: SenderService
  private lateinit var ui: FakeUi
  private lateinit var controller: MainActivitySettingsController

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    prefs = PreferenceManager.getDefaultSharedPreferences(context)
    prefs.edit().clear().commit()
    sender = SenderService(PausedExecutorService())
    sender.keepaliveTimeout = 10000000 // disable keepalive noise
    ui = FakeUi()
    controller = MainActivitySettingsController(context, sender, ui)
  }

  /** Stores [value] under [key] and notifies the controller (the common act step). */
  private fun setPref(key: String, value: String) {
    prefs.edit().putString(key, value).commit()
    controller.onPreferenceChanged(prefs)
  }

  @Test
  fun onPreferenceChangedShouldSetTargetChipToHost() {
    setPref(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9")
    assertEquals("10.0.0.9", ui.chipText)
  }

  @Test
  fun onPreferenceChangedWithBlankHostShouldFallBackToVideoHost() {
    setPref(SettingsFragment.SK_HOST_ADDRESS, "")
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.9:8080/?action=stream")
    assertEquals("10.0.0.9", ui.chipText)
  }

  @Test
  fun onPreferenceChangedWithBlankHostAndNoVideoShouldShowFiller() {
    setPref(SettingsFragment.SK_HOST_ADDRESS, "")
    setPref(SettingsFragment.SK_VIDEO_URI, "")
    assertEquals(MainActivitySettingsController.TARGET_CHIP_EMPTY, ui.chipText)
  }

  @Test
  fun onPreferenceChangedWithUnchangedAddrShouldNotRewriteVideoUri() {
    // First pass establishes the address; a second identical pass keeps the
    // address the same -> the video-URI rewrite is skipped.
    controller.onPreferenceChanged(prefs)
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
    assertEquals(
        "http://10.0.0.7:8080/?action=stream",
        prefs.getString(SettingsFragment.SK_VIDEO_URI, ""),
    )
  }

  @Test
  fun onPreferenceChangedShouldClampPadsAlpha() {
    val cases = listOf("-50" to 0f, "999" to 1f)
    for ((value, expected) in cases) {
      setPref(SettingsFragment.SK_SHOW_PADS, value)
      assertEquals("pads alpha for '$value' must clamp", expected, ui.lastAlpha, 0.001f)
    }
  }

  @Test
  fun onPreferenceChangedWithIntSliderValuesShouldClamp() {
    // SeekBarPreference stores Int — the readInt helper must handle it.
    prefs.edit().putInt(SettingsFragment.SK_SHOW_PADS, 255).commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("max alpha", 1f, ui.lastAlpha, 0.001f)

    prefs.edit().putInt(SettingsFragment.SK_WHEEL_STEP, 999).commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("wheel step clamped to 100", 100, ui.wheelStep)
  }

  @Test
  fun onPreferenceChangedWithWheelEnabledSwitchShouldApply() {
    prefs.edit().putBoolean(SettingsFragment.SK_WHEEL_ENABLED, true).commit()
    controller.onPreferenceChanged(prefs)
    assertTrue(ui.wheelEnabled)
  }

  @Test
  fun onPreferenceChangedWithKeepScreenOnSwitchShouldApply() {
    prefs.edit().putBoolean(SettingsFragment.SK_KEEP_SCREEN_ON, false).commit()
    controller.onPreferenceChanged(prefs)
    assertTrue(!ui.keepScreenOnState)
  }

  @Test
  fun onPreferenceChangedWithEmptyVideoUriShouldNullTheUrl() {
    // Establish the default address first so the video-URI rewrite-on-host-change
    // does not overwrite the empty value we set next.
    controller.onPreferenceChanged(prefs)
    setPref(SettingsFragment.SK_VIDEO_URI, "")
    assertNull(ui.url)
  }

  @Test
  fun onPreferenceChangedWithEmptyHostShouldNullTheVideoUrlDefault() {
    // Empty host + unset URI pref: the default is "" (placeholder), not the malformed
    // "http://:8080/..." which used to toast "Illegal video stream URL" on every register.
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "").commit()
    controller.onPreferenceChanged(prefs)
    assertNull(ui.url)
  }

  @Test
  fun onPreferenceChangedWithVideoUriShouldSetUrl() {
    controller.onPreferenceChanged(prefs)
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
    assertEquals("http://10.0.0.7:8080/?action=stream", ui.url)
  }

  @Test
  fun onPreferenceChangedWithUnsetVideoUriShouldDeriveFromHost() {
    // No URI stored: the effective URI is derived from the host (the settings row shows the same
    // derived URL — they share SettingsFragment.effectiveVideoUri).
    setPref(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9")
    assertEquals("http://10.0.0.9:8080/?action=stream", ui.url)
  }

  @Test
  fun effectiveVideoUriShouldMatchControllerDerivation() {
    // The settings-row helper and the runtime controller must never disagree: same derivation,
    // same empty-vs-derived semantics.
    setPref(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9")
    assertEquals(
        "derived URL must match the runtime value",
        ui.url,
        SettingsFragment.effectiveVideoUri(prefs),
    )
    setPref(SettingsFragment.SK_VIDEO_URI, "")
    assertNull("explicitly-empty URI must disable video", ui.url)
    assertEquals(
        "explicitly-empty URI must also empty the helper",
        "",
        SettingsFragment.effectiveVideoUri(prefs),
    )
  }

  @Test
  fun effectiveVideoUriWithHostOverrideShouldUseOverride() {
    assertEquals(
        "http://override.host:8080/?action=stream",
        SettingsFragment.effectiveVideoUri(prefs, "override.host"),
    )
  }

  @Test
  fun registerShouldApplyDefaultPreferences() {
    controller.register()
    try {
      // register() calls onPreferenceChanged with the (empty) prefs -> the
      // defaults are applied: default address + default port.
      assertEquals("192.168.77.1", sender.hostAddr)
      assertEquals(7, ui.wheelStep)
    } finally {
      // Do not leak the shared-prefs listener into later tests in this JVM.
      controller.unregister()
    }
  }

  @Test
  fun doubleRegisterShouldNotDoubleApplyOrLeak() {
    controller.register()
    controller.register() // idempotent: second call is a no-op
    try {
      assertEquals("192.168.77.1", sender.hostAddr)
      // A single unregister still releases the listener (no double-register leak).
      controller.unregister()
      controller.unregister() // idempotent no-op
    } finally {
      controller.unregister()
    }
  }

  @Test
  fun onPreferenceChangedShouldApplyOrClampWheelStep() {
    val cases = listOf("42" to 42, "not-a-number" to 7, "500" to 100)
    for ((value, expected) in cases) {
      // A non-numeric value keeps the CURRENT step, so reset the fake's default
      // per row to reproduce the fresh-state condition each case expects.
      ui.wheelStep = 7
      setPref(SettingsFragment.SK_WHEEL_STEP, value)
      assertEquals("wheel step for '$value'", expected, ui.wheelStep)
    }
  }

  @Test
  fun onPreferenceChangedWithDefaultMagicButtonsShouldApplyDefaults() {
    controller.onPreferenceChanged(prefs)
    assertEquals(3, ui.magicCount)
    assertEquals(listOf("▲", "■", "●", "✕", "◆"), ui.magicSymbols)
    assertEquals(100, ui.magicSizePercent)
  }

  @Test
  fun onPreferenceChangedWithMagicButtonSizeShouldClamp() {
    controller.onPreferenceChanged(prefs)
    assertEquals("default", 100, ui.magicSizePercent)

    prefs.edit().putString(SettingsFragment.SK_MAGIC_BUTTON_SIZE, "50").commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("clamped to min", 70, ui.magicSizePercent)

    prefs.edit().putString(SettingsFragment.SK_MAGIC_BUTTON_SIZE, "200").commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("clamped to max", 150, ui.magicSizePercent)

    prefs.edit().putInt(SettingsFragment.SK_MAGIC_BUTTON_SIZE, 130).commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("in-range int", 130, ui.magicSizePercent)
  }

  @Test
  fun onPreferenceChangedWithMagicButtonCountShouldClamp() {
    prefs.edit().putInt(SettingsFragment.SK_MAGIC_BUTTON_COUNT, 5).commit()
    controller.onPreferenceChanged(prefs)
    assertEquals(5, ui.magicCount)

    prefs.edit().putInt(SettingsFragment.SK_MAGIC_BUTTON_COUNT, 999).commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("clamped to max", 5, ui.magicCount)

    prefs.edit().putString(SettingsFragment.SK_MAGIC_BUTTON_COUNT, "0").commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("zero hides the row", 0, ui.magicCount)
  }

  @Test
  fun onPreferenceChangedShouldResolveStoredSymbols() {
    prefs.edit().putString(SettingsFragment.magicSymbolKey(1), "★").commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("★", ui.magicSymbols[0])
    assertEquals("■", ui.magicSymbols[1]) // untouched -> default glyph
  }

  @Test
  fun onPreferenceChangedWithHideControlsShouldDriveVisibility() {
    controller.onPreferenceChanged(prefs)
    assertTrue(ui.controlsVisibleState)
    prefs.edit().putBoolean(SettingsFragment.SK_HIDE_CONTROLS, true).commit()
    controller.onPreferenceChanged(prefs)
    assertTrue(!ui.controlsVisibleState)
  }

  @Test
  fun onPreferenceChangedWithEmptyHostShouldHideControlsRegardlessOfToggle() {
    // Empty host = video-only device: no pads/buttons, even with the toggle unset.
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "").commit()
    controller.onPreferenceChanged(prefs)
    assertTrue("empty host must hide pads with the toggle unset", !ui.controlsVisibleState)
    // Re-setting a host restores the controls.
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.7").commit()
    controller.onPreferenceChanged(prefs)
    assertTrue(ui.controlsVisibleState)
  }

  @Test
  fun onPreferenceChangedWithShowFpsShouldToggle() {
    controller.onPreferenceChanged(prefs)
    assertTrue(!ui.showFpsState)
    prefs.edit().putBoolean(SettingsFragment.SK_SHOW_FPS, true).commit()
    controller.onPreferenceChanged(prefs)
    assertTrue(ui.showFpsState)
  }

  @Test
  fun readMagicButtonCountShouldHonorIntStringAndDefaults() {
    assertEquals(
        "empty prefs -> default",
        3,
        MainActivitySettingsController.readMagicButtonCount(prefs),
    )
    prefs.edit().putInt(SettingsFragment.SK_MAGIC_BUTTON_COUNT, 2).commit()
    assertEquals(2, MainActivitySettingsController.readMagicButtonCount(prefs))
    prefs.edit().putString(SettingsFragment.SK_MAGIC_BUTTON_COUNT, "7").commit()
    assertEquals("clamped to max", 5, MainActivitySettingsController.readMagicButtonCount(prefs))
    prefs.edit().putString(SettingsFragment.SK_MAGIC_BUTTON_COUNT, "garbage").commit()
    assertEquals(
        "garbage -> default",
        3,
        MainActivitySettingsController.readMagicButtonCount(prefs),
    )
  }

  @Test
  fun onPreferenceChangedWithVideoCropShouldToggle() {
    controller.onPreferenceChanged(prefs)
    assertTrue(!ui.cropToFillState)
    prefs.edit().putBoolean(SettingsFragment.SK_VIDEO_CROP, true).commit()
    controller.onPreferenceChanged(prefs)
    assertTrue(ui.cropToFillState)
  }

  @Test
  fun onPreferenceChangedWithIntShowPadsShouldClampAlpha() {
    prefs.edit().putInt(SettingsFragment.SK_SHOW_PADS, 50).commit()
    controller.onPreferenceChanged(prefs)
    assertEquals(50f / 255f, ui.lastAlpha, 0.001f)
  }

  @Test
  fun onPreferenceChangedWithUdpTransportSetsUdpMode() {
    setPref(SettingsFragment.SK_TRANSPORT, "udp")
    assertEquals(TransportMode.UDP, sender.transportMode)
  }

  @Test
  fun onPreferenceChangedWithMissingTransportKeepsTcpDefault() {
    controller.onPreferenceChanged(prefs)
    assertEquals(TransportMode.TCP, sender.transportMode)
  }

  @Test
  fun customMessagePrefTriggersCustomCommand() {
    AppLog.minBufferLevel = Log.DEBUG
    AppLog.clearForTest()
    prefs.edit().putString(SettingsFragment.SK_CUSTOM_MESSAGE, "hello").commit()
    // A real change event carries the changed key (the register sweep passes null).
    controller.onPreferenceChanged(prefs, SettingsFragment.SK_CUSTOM_MESSAGE)
    val logs = AppLog.tail(100)
    assertTrue(logs.any { it.contains("Sending 'custom hello'") })
  }

  @Test
  fun unrelatedPrefChangeShouldNotResendCustomCommand() {
    AppLog.minBufferLevel = Log.DEBUG
    AppLog.clearForTest()
    prefs.edit().putString(SettingsFragment.SK_CUSTOM_MESSAGE, "hello").commit()
    prefs.edit().putBoolean(SettingsFragment.SK_SHOW_FPS, true).commit()
    // The custom command is an edge on ITS key change only: an unrelated setting (here FPS) must
    // not re-send `custom <message>` to the robot (the old code re-sent it on every pref change).
    controller.onPreferenceChanged(prefs, SettingsFragment.SK_SHOW_FPS)
    assertTrue(
        "an unrelated pref change must not re-send the custom message",
        AppLog.tail(100).none { it.contains("Sending 'custom hello'") },
    )
  }

  @Test
  fun registerSweepShouldNotSendCustomCommand() {
    AppLog.minBufferLevel = Log.DEBUG
    AppLog.clearForTest()
    prefs.edit().putString(SettingsFragment.SK_CUSTOM_MESSAGE, "hello").commit()
    // register() applies the stored prefs without a changed key -> the custom edge must not fire.
    controller.register()
    try {
      assertTrue(
          "the register sweep must not re-send the custom message",
          AppLog.tail(100).none { it.contains("Sending 'custom hello'") },
      )
    } finally {
      controller.unregister()
    }
  }
}
