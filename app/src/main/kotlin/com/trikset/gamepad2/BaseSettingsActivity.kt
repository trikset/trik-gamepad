package com.trikset.gamepad2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen

/**
 * Shared settings-screen host: shows a [SettingsFragment] built from the subclass's preference XML
 * and re-runs the same fragment against a tapped nested [PreferenceScreen] root (the androidx 1.2.x
 * sub-screen callback; see the subclass docs). The two settings screens (app and robot/target)
 * differ only in which XML they load and their label.
 */
abstract class BaseSettingsActivity :
    AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

  /** The preference resource loaded by this settings screen (pref_app.xml / pref_robot.xml). */
  protected abstract val preferenceXml: Int

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportFragmentManager
        .beginTransaction()
        .replace(android.R.id.content, SettingsFragment.newInstance(preferenceXml))
        .commit()
  }

  /**
   * PreferenceFragmentCompat 1.2.x has no default sub-screen navigation: a tapped nested
   * PreferenceScreen is silently ignored unless the host handles it (verified against the library
   * bytecode). Re-run [SettingsFragment] against the nested screen's root key so its init* helpers
   * are wired for the sub-screen too; the back stack pops back to the root screen.
   */
  override fun onPreferenceStartScreen(
      caller: PreferenceFragmentCompat,
      pref: PreferenceScreen,
  ): Boolean {
    val args = Bundle()
    args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
    // Preserve the app/robot XML choice when navigating into a nested screen.
    val callerXml = caller.arguments?.getInt(SettingsFragment.ARG_PREFERENCE_XML, 0) ?: 0
    if (callerXml != 0) {
      args.putInt(SettingsFragment.ARG_PREFERENCE_XML, callerXml)
    }
    val fragment = SettingsFragment()
    fragment.arguments = args
    supportFragmentManager
        .beginTransaction()
        .replace(android.R.id.content, fragment)
        .addToBackStack(null)
        .commit()
    return true
  }
}
