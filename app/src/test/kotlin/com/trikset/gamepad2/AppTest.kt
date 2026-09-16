package com.trikset.gamepad2

import android.util.Log
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.diagnostics.AppLog
import com.trikset.gamepad2.diagnostics.CrashHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppTest : RobolectricTestBase() {

  // The manifest declares .App, so the Robolectric runtime application IS an App
  // and its onCreate has run at test start. The level tests clear the pref and
  // re-run onCreate (idempotent: re-applies the level, reinstalls the handler)
  // to observe the wiring without relying on cross-test pref state.
  private fun reinitializedApp(): App {
    val app = RuntimeEnvironment.getApplication() as App
    PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit()
    return app
  }

  @Test
  fun appliesDefaultDiagnosticsLevelWhenPrefUnset() {
    val app = reinitializedApp()
    app.onCreate()
    assertEquals(Log.INFO.toLong(), AppLog.minBufferLevel.toLong())
  }

  @Test
  fun appliesPersistedDiagnosticsLevel() {
    val app = reinitializedApp()
    PreferenceManager.getDefaultSharedPreferences(app)
        .edit()
        .putString(SettingsFragment.SK_DIAG_LEVEL, "verbose")
        .commit()
    app.onCreate()
    assertEquals(Log.VERBOSE.toLong(), AppLog.minBufferLevel.toLong())
  }

  @Test
  fun installsChainingCrashHandler() {
    assertTrue(Thread.getDefaultUncaughtExceptionHandler() is CrashHandler)
  }
}
