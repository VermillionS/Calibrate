package com.example.calibrate.ui;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import com.example.calibrate.R;
import com.example.calibrate.data.Prediction;
import com.example.calibrate.vm.PredictionViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private static final String KEY_THEME = "pref_theme";
    private PredictionViewModel vm;

    // SAF launchers
    private final ActivityResultLauncher<String> createDocLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                    uri -> { if (uri != null) doExport(uri); });

    private final ActivityResultLauncher<String[]> openDocLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> { if (uri != null) confirmImport(uri); });

    public SettingsFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.frag_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        vm = new androidx.lifecycle.ViewModelProvider(requireActivity()).get(PredictionViewModel.class);

        // THEME spinner (as you had it)
        Spinner spTheme = root.findViewById(R.id.spTheme);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.pref_theme_entries, R.layout.spinner_item);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spTheme.setAdapter(adapter);

        final String[] values = getResources().getStringArray(R.array.pref_theme_values);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final String saved = prefs.getString(KEY_THEME, "system");

        int sel = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(saved)) { sel = i; break; }
        spTheme.setSelection(sel, false);

        final boolean[] userTouch = {false};
        spTheme.setOnTouchListener((v, e) -> { userTouch[0] = true; return false; });
        spTheme.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!userTouch[0] && first) { first = false; return; }
                String newValue = values[position];
                prefs.edit().putString(KEY_THEME, newValue).apply();
                requireActivity().recreate();
                userTouch[0] = false;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        // --- Buttons ---
        root.findViewById(R.id.btnExport).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Export predictions?")
                    .setMessage("Save all predictions to a JSON file?")
                    .setPositiveButton("Export", (d,w)-> {
                        String ts = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(System.currentTimeMillis());
                        createDocLauncher.launch("calibrate-backup-" + ts + ".json");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        root.findViewById(R.id.btnImport).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Import predictions?")
                    .setMessage("This will DELETE all current predictions and REPLACE them with those from a JSON file.")
                    .setPositiveButton("Choose file", (d,w)-> openDocLauncher.launch(new String[]{"application/json","*/*"}))
                    .setNegativeButton("Cancel", null)
                    .show();
        });

//        Below will show a button to delete all predictions
//        root.findViewById(R.id.btnDeleteAll).setOnClickListener(v -> {
//            new MaterialAlertDialogBuilder(requireContext())
//                    .setTitle("Delete ALL predictions?")
//                    .setMessage("This cannot be undone.")
//                    .setPositiveButton("Delete", (d,w)-> {
//                        vm.deleteAll();
//                        Toast.makeText(requireContext(), "DANGER! This will DELETE all predictions, are you very sure you want to do this?", Toast.LENGTH_SHORT).show();
//                    })
//                    .setNegativeButton("Cancel", null)
//                    .show();
//        });
    }

    private void doExport(@NonNull Uri uri) {
        new Thread(() -> {
            try {
                List<Prediction> list = vm.exportAllSync();
                JSONArray arr = new JSONArray();
                for (Prediction p : list) {
                    JSONObject o = new JSONObject();
                    o.put("id", p.id);
                    o.put("title", p.title);
                    o.put("probability", p.probability);
                    o.put("createdAt", p.createdAt);
                    o.put("resolved", p.resolved);
                    // outcomeYes may be null
                    o.put("outcomeYes", p.outcomeYes == null ? JSONObject.NULL : p.outcomeYes);
                    o.put("description", p.description == null ? JSONObject.NULL : p.description);
                    o.put("tagLabel", p.tagLabel == null ? JSONObject.NULL : p.tagLabel);
                    o.put("tagColor", p.tagColor);
                    arr.put(o);
                }
                try (OutputStreamWriter w =
                             new OutputStreamWriter(requireContext().getContentResolver().openOutputStream(uri), "UTF-8")) {
                    w.write(arr.toString(2));
                }
                runOnUi(() -> Toast.makeText(requireContext(), "Exported " + list.size() + " predictions", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUi(() -> Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void confirmImport(@NonNull Uri uri) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Replace current data?")
                .setMessage("Importing will delete all existing predictions first. Continue?")
                .setPositiveButton("Import", (d,w)-> doImport(uri))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doImport(@NonNull Uri uri) {
        new Thread(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(
                        requireContext().getContentResolver().openInputStream(uri), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append('\n');
                }
                JSONArray arr = new JSONArray(sb.toString());
                List<Prediction> out = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Prediction p = new Prediction();
                    p.id = 0;
                    p.title = o.optString("title", "");
                    p.probability = o.optDouble("probability", 50.0);
                    p.createdAt = o.optLong("createdAt", System.currentTimeMillis());
                    p.resolved = o.optBoolean("resolved", false);
                    p.outcomeYes = o.isNull("outcomeYes") ? null : (o.optBoolean("outcomeYes") ? Boolean.TRUE : Boolean.FALSE);
                    p.description = o.isNull("description") ? null : o.optString("description", null);
                    p.tagLabel = o.isNull("tagLabel") ? null : o.optString("tagLabel", null);
                    p.tagColor = o.optInt("tagColor", 0);
                    out.add(p);
                }
                vm.importReplaceAll(out);
                runOnUi(() -> Toast.makeText(requireContext(), "Imported " + out.size() + " predictions", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUi(() -> Toast.makeText(requireContext(), "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void runOnUi(@NonNull Runnable r) {
        if (!isAdded()) return;
        new Handler(Looper.getMainLooper()).post(r);
    }

    public static String getSavedTheme(@NonNull android.content.Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sp.getString(KEY_THEME, "system");
    }
}