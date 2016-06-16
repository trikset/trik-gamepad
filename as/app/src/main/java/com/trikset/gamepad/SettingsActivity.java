package com.trikset.gamepad;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.support.annotation.NonNull;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;

public class SettingsActivity extends PreferenceActivity {

    public static final String SK_HOST_ADDRESS = "hostAddress";
    public static final String SK_HOST_PORT    = "hostPort";
    public static final String SK_SHOW_PADS    = "showPads";
    public static final String SK_VIDEO_URI    = "videoURI";
    public static final String SK_WHEEL_STEP   = "wheelSens";

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.button1:
            // case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPostCreate(final Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_general);
        final OnPreferenceChangeListener listner = new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(@NonNull final Preference preference,
                                              @NonNull final Object value) {
                preference.setSummary(value.toString());
                return true;
            }
        };
        for (final String prefKey : new String[] { SK_HOST_ADDRESS,
                SK_HOST_PORT }) {
            final Preference pref = findPreference(prefKey);
            pref.setSummary(pref.getSharedPreferences().getString(prefKey, ""));
            pref.setOnPreferenceChangeListener(listner);
        }
    }
}
