package com.trikset.gamepad2.diagnostics

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.BuildConfig
import com.trikset.gamepad2.ConnectionState
import com.trikset.gamepad2.RobotPresetStore
import com.trikset.gamepad2.SettingsFragment
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DiagnosticsReportTest {

  private lateinit var context: Context
  private lateinit var prefs: SharedPreferences

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    prefs = PreferenceManager.getDefaultSharedPreferences(context)
    prefs.edit().clear().commit()
    // fromAppState reads the latest crash; keep the store empty so no stale record leaks in.
    File(context.filesDir, CrashLogStore.CRASH_DIR).deleteRecursively()
  }

  @Test
  fun buildCarriesAppAndDeviceIdentity() {
    val report = DiagnosticsReport.build(context, prefs, null, emptyList(), null)
    assertTrue(report.contains("# TRIK Gamepad diagnostic report"))
    assertTrue(report.contains("Version: ${BuildConfig.VERSION_NAME}"))
    assertTrue(report.contains("Build type: ${BuildConfig.BUILD_TYPE}"))
    assertTrue(report.contains("Manufacturer: ${android.os.Build.MANUFACTURER}"))
    assertTrue(report.contains("Model: ${android.os.Build.MODEL}"))
    assertTrue(report.contains("Android: ${android.os.Build.VERSION.RELEASE}"))
  }

  @Test
  fun buildCarriesDisplayInfo() {
    val report = DiagnosticsReport.build(context, prefs, null, emptyList(), null)
    assertTrue(report.contains("Resolution: ${context.resources.displayMetrics.widthPixels}x"))
    assertTrue(report.contains("font scale"))
    assertTrue(report.contains("Locale:"))
  }

  @Test
  fun connectionStateIsRenderedForEachVariant() {
    assertTrue(
        DiagnosticsReport.build(context, prefs, ConnectionState.Connected, emptyList(), null)
            .contains("State: Connected")
    )
    assertTrue(
        DiagnosticsReport.build(context, prefs, ConnectionState.Connecting, emptyList(), null)
            .contains("State: Connecting")
    )
    assertTrue(
        DiagnosticsReport.build(
                context,
                prefs,
                ConnectionState.Disconnected("robot away"),
                emptyList(),
                null,
            )
            .contains("State: Disconnected (robot away)")
    )
    assertTrue(
        DiagnosticsReport.build(context, prefs, null, emptyList(), null).contains("not running")
    )
  }

  @Test
  fun defaultSettingsAreMarkedAsDefault() {
    val report = DiagnosticsReport.build(context, prefs, null, emptyList(), null)
    assertTrue(report.contains("Robot IP address: 192.168.77.1 (default)"))
    assertTrue(report.contains("Robot TCP port: 4444 (default)"))
    assertTrue(report.contains("Button size: 100 (default)"))
    assertTrue(report.contains("Smart glyph alignment: true (default)"))
    assertTrue(report.contains("Diagnostics verbosity: info (default)"))
  }

  @Test
  fun changedSettingsAreShownWithoutDefaultMarker() {
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_PORT, "5555").commit()
    val report = DiagnosticsReport.build(context, prefs, null, emptyList(), null)
    assertTrue(report.contains("Robot IP address: 10.0.0.9"))
    assertFalse(report.contains("Robot IP address: 10.0.0.9 (default)"))
    assertTrue(report.contains("Robot TCP port: 5555"))
  }

  @Test
  fun magicSymbolsAndPresetsAreListed() {
    prefs.edit().putString(SettingsFragment.magicSymbolKey(1), "X").commit()
    RobotPresetStore(prefs).save("workshop", "10.0.0.9", "4444", "http://10.0.0.9:8080/")
    val report = DiagnosticsReport.build(context, prefs, null, emptyList(), null)
    assertTrue(report.contains("- Button 1 symbol: X"))
    assertTrue(report.contains("Robot presets: workshop"))
  }

  @Test
  fun emptyPresetListReadsAsNone() {
    val report = DiagnosticsReport.build(context, prefs, null, emptyList(), null)
    assertTrue(report.contains("Robot presets: none"))
  }

  @Test
  fun logTailIsEmbeddedInAFencedBlock() {
    val report = DiagnosticsReport.build(context, prefs, null, listOf("line one", "line two"), null)
    assertTrue(report.contains("```text"))
    assertTrue(report.contains("line one"))
    assertTrue(report.contains("line two"))
  }

  @Test
  fun emptyLogTailShowsPlaceholder() {
    val report = DiagnosticsReport.build(context, prefs, null, emptyList(), null)
    assertTrue(report.contains("(no log entries)"))
  }

  @Test
  fun crashTraceAppearsOnlyWhenProvided() {
    val withCrash =
        DiagnosticsReport.build(context, prefs, null, emptyList(), "java.lang.Boom: oops")
    assertTrue(withCrash.contains("## Last crash"))
    assertTrue(withCrash.contains("java.lang.Boom: oops"))

    val withoutCrash = DiagnosticsReport.build(context, prefs, null, emptyList(), null)
    assertFalse(withoutCrash.contains("## Last crash"))
  }

  @Test
  fun fromAppStateBuildsFromDefaultPrefsAndLatestCrash() {
    CrashLogStore(context).save("java.lang.IllegalState: stuck")
    val report = DiagnosticsReport.fromAppState(context)
    assertTrue(report.contains("# TRIK Gamepad diagnostic report"))
    assertTrue(report.contains("java.lang.IllegalState: stuck"))
    assertTrue(report.contains("Robot IP address: 192.168.77.1 (default)"))
  }
}
