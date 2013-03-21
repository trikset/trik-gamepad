package com.trik.car;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;
import com.trik.handy.R;

public class SettingsActivity extends PreferenceActivity {

    public static final String SK_HOST_ADDRESS = "hostAddress";

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
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

        Preference hostAddr = findPreference(SK_HOST_ADDRESS);
        hostAddr.setOnPreferenceChangeListener(listner);
        listner.onPreferenceChange(hostAddr, PreferenceManager.getDefaultSharedPreferences(hostAddr.getContext())
                .getString(hostAddr.getKey(), ""));
    }
}
