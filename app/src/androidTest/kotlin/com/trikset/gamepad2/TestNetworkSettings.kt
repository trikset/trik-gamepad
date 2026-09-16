package com.trikset.gamepad2

import android.app.Activity
import androidx.preference.PreferenceManager

/**
 * Sets the network preferences used by the instrumented tests: the robot host, port, and a
 * keepalive large enough that no keepalive message interferes with the assertions. Shared by the
 * MainWindowTests inner classes.
 */
fun initNetworkSettings(activity: Activity) {
  val preferenceEditor = PreferenceManager.getDefaultSharedPreferences(activity).edit()
  preferenceEditor.putString(SettingsFragment.SK_HOST_ADDRESS, DummyServer.IP)
  preferenceEditor.putString(SettingsFragment.SK_HOST_PORT, DummyServer.DEFAULT_PORT.toString())
  // In order not to receive keep-alive messages
  preferenceEditor.putString(SettingsFragment.SK_KEEPALIVE, "100000000")
  preferenceEditor.commit()
}
