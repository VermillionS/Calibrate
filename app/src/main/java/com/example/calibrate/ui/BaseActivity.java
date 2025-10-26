package com.example.calibrate.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        getDelegate().applyDayNight();
        super.onCreate(savedInstanceState);
    }
}
