package com.trik.gamepad

import android.os.Bundle
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.PreferenceActivity
import android.support.v4.app.NavUtils
import android.view.MenuItem

class SettingsActivity : PreferenceActivity() {

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.button1 -> {
                // case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_general)
        val listner = object : OnPreferenceChangeListener {
            override fun onPreferenceChange(preference: Preference,
                                            value: Any): Boolean {
                preference.summary = value.toString()
                return true
            }
        }
        for (prefKey in arrayOf(SK_HOST_ADDRESS, SK_HOST_PORT)) {
            val pref = findPreference(prefKey)
            pref.summary = pref.sharedPreferences.getString(prefKey, "")
            pref.onPreferenceChangeListener = listner
        }
    }

    companion object {

        val SK_HOST_ADDRESS = "hostAddress"
        val SK_HOST_PORT = "hostPort"
        val SK_SHOW_PADS = "showPads"
        val SK_VIDEO_URI = "videoURI"
        val SK_WHEEL_STEP = "wheelSens"
    }
}
