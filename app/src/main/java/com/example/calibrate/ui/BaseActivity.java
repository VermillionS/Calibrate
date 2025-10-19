package com.example.calibrate.ui;

import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import com.example.calibrate.R;

public abstract class BaseActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String theme = SettingsFragment.getSavedTheme(this);
        switch (theme) {
            case "Dark":    setTheme(R.style.Theme_Calibrate_Dark); break;
            case "Light":  setTheme(R.style.Theme_Calibrate_Light); break;
            default:       setTheme(R.style.Base_Theme_Calibrate);
        }
        super.onCreate(savedInstanceState);
    }
}
