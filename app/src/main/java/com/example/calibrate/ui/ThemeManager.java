package com.example.calibrate.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.example.calibrate.R;

public final class ThemeManager {
    private ThemeManager(){}

    public static void apply(Activity a) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(a);
        String v = sp.getString("pref_theme", "system");

        switch (v) {
            case "Light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                a.setTheme(R.style.Theme_Calibrate_Light);
                break;
            case "Dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                a.setTheme(R.style.Theme_Calibrate_Dark);
                break;
            case "system":
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                a.setTheme(R.style.Theme_Calibrate_Dark);
                break;
        }
    }
}