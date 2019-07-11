package com.trikset.gamepad;

import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Locale;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String SK_HOST_ADDRESS  = "hostAddress";
    public static final String SK_HOST_PORT     = "hostPort";
    public static final String SK_SHOW_PADS     = "showPads";
    public static final String SK_VIDEO_URI     = "videoURI";
    public static final String SK_WHEEL_STEP    = "wheelSens";
    public static final String SK_ABOUT_SYSTEM  = "aboutSystem";

    private void initializeAboutSystemField() {
        final Preference aboutSystem = findPreference(SK_ABOUT_SYSTEM);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        final String systemInfo = String.format(
                Locale.ENGLISH,
                "Android %s; SDK %d; Resolution %d x %d (%f x %f PPI)",
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                displayMetrics.heightPixels,
                displayMetrics.widthPixels,
                displayMetrics.ydpi,
                displayMetrics.xdpi);
        aboutSystem.setSummary(systemInfo);
    }

    private void initializeDynamicPreferenceSummary() {
        final Preference.OnPreferenceChangeListener listener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(@NonNull final Preference preference,
                                              @NonNull final Object value) {
                preference.setSummary(value.toString());
                return true;
            }
        };

        for (final String preferenceKey : new String[] { SK_HOST_ADDRESS, SK_HOST_PORT }) {
            final Preference preference = findPreference(preferenceKey);
            preference.setSummary(
                    preference.getSharedPreferences().getString(preferenceKey, ""));
            preference.setOnPreferenceChangeListener(listener);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_general, rootKey);

        initializeAboutSystemField();
        initializeDynamicPreferenceSummary();
    }
}
