package com.trikset.gamepad2

/** App settings: how the app looks and behaves on this device (see [SettingsFragment]). */
class SettingsActivity : BaseSettingsActivity() {
  override val preferenceXml: Int = R.xml.pref_app
}
