package com.example.calibrate.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.calibrate.R;
import com.example.calibrate.data.Prediction;
import com.example.calibrate.vm.PredictionViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.*;
import java.util.*;

public class ScoreFragment extends Fragment {
    private PredictionViewModel vm;
    private LineChart chart;
    private Spinner windowSpinner;
    private int windowDays = 30;

    public ScoreFragment() { super(R.layout.frag_score); }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        chart = root.findViewById(R.id.chartScore);
        windowSpinner = root.findViewById(R.id.spinnerWindow);

        vm = new ViewModelProvider(requireActivity()).get(PredictionViewModel.class);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"7 days", "30 days", "90 days"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        windowSpinner.setAdapter(adapter);
        windowSpinner.setSelection(1);

        windowSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                windowDays = (pos == 0) ? 7 : (pos == 1) ? 30 : 90;
                refreshChart();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        vm.predictions().observe(getViewLifecycleOwner(), list -> refreshChart());
    }

    private void refreshChart() {
        List<Prediction> preds = vm.predictions().getValue();
        if (preds == null || preds.isEmpty()) { chart.clear(); return; }

        preds.sort(Comparator.comparingLong(p -> p.createdAt));
        ArrayList<Entry> brierEntries = new ArrayList<>();
        ArrayList<Entry> logEntries = new ArrayList<>();

        long windowMillis = windowDays * 24L * 3600L * 1000L;
        for (int i = 0; i < preds.size(); i++) {
            long cutoff = preds.get(i).createdAt - windowMillis;
            List<Prediction> window = new ArrayList<>();
            for (Prediction p : preds)
                if (p.resolved && p.outcomeYes != null && p.createdAt >= cutoff && p.createdAt <= preds.get(i).createdAt)
                    window.add(p);

            if (window.isEmpty()) continue;

            double brier = 0, logloss = 0;
            for (Prediction p : window) {
                double prob = p.probability / 100.0;
                double outcome = p.outcomeYes ? 1.0 : 0.0;
                brier += Math.pow(prob - outcome, 2);
                logloss += -(outcome * Math.log(Math.max(1e-9, prob)) + (1 - outcome) * Math.log(Math.max(1e-9, 1 - prob)));
            }
            brier /= window.size();
            logloss /= window.size();

            brierEntries.add(new Entry(i, (float)brier));
            logEntries.add(new Entry(i, (float)logloss));
        }

        LineDataSet dsBrier = new LineDataSet(brierEntries, "Brier score");
        dsBrier.setColor(Color.CYAN);
        dsBrier.setCircleRadius(3f);

        LineDataSet dsLog = new LineDataSet(logEntries, "Log loss");
        dsLog.setColor(Color.MAGENTA);
        dsLog.setCircleRadius(3f);

        LineData data = new LineData(dsBrier, dsLog);
        chart.setData(data);
        chart.invalidate();
    }
}