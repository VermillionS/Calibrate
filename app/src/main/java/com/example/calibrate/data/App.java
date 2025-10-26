package com.example.calibrate.data;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.calibrate.R;

public class App extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        String v = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString("pref_theme", "system");

        switch (v) {
            case "Light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                setTheme(R.style.Theme_Calibrate_Light);
                break;
            case "Dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                setTheme(R.style.Theme_Calibrate_Dark);
                break;
            case "system":
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                setTheme(R.style.Theme_Calibrate_Dark);
                break;
        }
    }
}
