package com.trikset.gamepad;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Locale;

import static android.content.Context.CLIPBOARD_SERVICE;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String SK_HOST_ADDRESS  = "hostAddress";
    public static final String SK_HOST_PORT     = "hostPort";
    public static final String SK_SHOW_PADS     = "showPads";
    public static final String SK_VIDEO_URI     = "videoURI";
    public static final String SK_WHEEL_STEP    = "wheelSens";
    public static final String SK_ABOUT_SYSTEM  = "aboutSystem";
    public static final String SK_KEEPALIVE     = "keepaliveTimeout";

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
        aboutSystem.setSummary("Tap to copy: " + systemInfo);

        // Copying system info to the clipboard on click
        final Preference.OnPreferenceClickListener listener = new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ClipboardManager clipboard =
                        (ClipboardManager) getActivity().getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Info about system", systemInfo);
                clipboard.setPrimaryClip(clip);

                final Toast copiedToClipboardToast = Toast.makeText(
                        getActivity().getApplicationContext(),
                        "Copied to clipboard",
                        Toast.LENGTH_SHORT);
                copiedToClipboardToast.show();

                return true;
            }
        };
        aboutSystem.setOnPreferenceClickListener(listener);
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

        for (final String preferenceKey :
                new String[] { SK_HOST_ADDRESS, SK_HOST_PORT, SK_KEEPALIVE }) {
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
