package com.trikset.gamepad2

import android.app.Application
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.diagnostics.AppLog
import com.trikset.gamepad2.diagnostics.CrashHandler
import com.trikset.gamepad2.diagnostics.CrashLogStore
import com.trikset.gamepad2.diagnostics.DiagLevel

/**
 * App-wide initialisation: applies the persisted diagnostics-verbosity setting to [AppLog] before
 * anything logs, and installs the chaining uncaught-exception handler so a crash is captured for
 * the next-launch report dialog. Kept minimal and dependency-free so it is safe under Robolectric.
 */
class App : Application() {

  override fun onCreate() {
    super.onCreate()
    AppLog.minBufferLevel =
        DiagLevel.toBufferLevel(
            PreferenceManager.getDefaultSharedPreferences(this)
                .getString(SettingsFragment.SK_DIAG_LEVEL, null)
        )
    Thread.setDefaultUncaughtExceptionHandler(
        CrashHandler(CrashLogStore(this), Thread.getDefaultUncaughtExceptionHandler())
    )
    // Process-wide Wi-Fi tracker: the ONE network callback is registered here (not per connect
    // attempt or per video player) so the app stays under Android's per-app callback cap — the
    // unbounded per-construction registration is what ended in a
    // ConnectivityManager$TooManyRequestsException crash (see WifiNetworkTracker).
    WifiNetworkTracker.initialize(this)
  }
}
