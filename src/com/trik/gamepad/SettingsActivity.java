package com.trik.gamepad;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;

public class SettingsActivity extends PreferenceActivity {

    public static final String SK_HOST_ADDRESS = "hostAddress";
    public static final String SK_HOST_PORT    = "hostPort";

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.button1:
            // case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_general);
        OnPreferenceChangeListener listner = new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                preference.setSummary(value.toString());
                return true;
            }
        };
        for (String prefKey : new String[] { SK_HOST_ADDRESS, SK_HOST_PORT }) {
            Preference pref = findPreference(prefKey);
            pref.setSummary(pref.getSharedPreferences().getString(prefKey, ""));
            pref.setOnPreferenceChangeListener(listner);
        }
    }
}
