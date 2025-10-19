package com.example.calibrate.ui;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import com.example.calibrate.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView nav = findViewById(R.id.bottom_nav);

        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.action_home);
            switchTo(new HomeFragment());
        }

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_home)     { switchTo(new HomeFragment());    return true; }
            if (id == R.id.action_graphs)  { switchTo(new GraphsFragment()); return true; }
            if (id == R.id.action_settings) { switchTo(new SettingsFragment());return true; }
            return false;
        });
    }
    private void switchTo(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, f)
                .commit();
    }
}
