package com.example.calibrate.ui;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.calibrate.R;
import com.example.calibrate.data.Prediction;
import com.example.calibrate.data.TagStore;
import com.example.calibrate.vm.PredictionViewModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class GraphsFragment extends Fragment {

    public GraphsFragment() { super(R.layout.frag_graph); }

    private PredictionViewModel vm;
    private LineChart chart;
    private BarChart sharpnessChart;
    private TextView empty;
    private MaterialButtonToggleGroup toggle;
    private List<Prediction> all = java.util.Collections.emptyList();
    private final FilterState filter = new FilterState();
    enum Status { ANY, RESOLVED, UNRESOLVED, RESOLVED_YES, RESOLVED_NO }
    static class FilterState {
        @Nullable Long fromMs, toMs;
        @Nullable String tag;
        @Nullable Double pMin, pMax;
        Status status = Status.ANY;
        int binSize = 10;
    }
    enum ScoreWindowMode { D7, D30, D60, DATE_RANGE }
    private ScoreWindowMode scoreMode = ScoreWindowMode.D30;

    @Override public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        chart = root.findViewById(R.id.chart);
        sharpnessChart = root.findViewById(R.id.sharpnessChart);
        empty = root.findViewById(R.id.tvEmpty);
        toggle = root.findViewById(R.id.toggleGroup);

        vm = new ViewModelProvider(requireActivity()).get(PredictionViewModel.class);

        vm.predictions().observe(getViewLifecycleOwner(), list -> {
            all = (list == null) ? java.util.Collections.emptyList() : list;
            renderFiltered();
        });

        View btnFilter = root.findViewById(R.id.btnFilter);
        if (btnFilter != null) btnFilter.setOnClickListener(v -> showFilterDialog());

        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnCalib) {
                chart.setVisibility(View.VISIBLE);
                sharpnessChart.setVisibility(View.GONE);
            } else if (checkedId == R.id.btnSharp) {
                chart.setVisibility(View.GONE);
                sharpnessChart.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.btnScore) {
                chart.setVisibility(View.VISIBLE);
                sharpnessChart.setVisibility(View.GONE);
        }

            renderFiltered();
        });

        toggle.check(R.id.btnCalib);
    }

    private void renderFiltered() {
        List<Prediction> subset = new ArrayList<>();
        for (Prediction p : all) if (passes(p)) subset.add(p);

        int checked = toggle.getCheckedButtonId();
        boolean showCalib = (checked == R.id.btnCalib);
        boolean showSharp = (checked == R.id.btnSharp);
        boolean showScore = (checked == R.id.btnScore);

        if (showCalib) {
            LineData data = buildCalibrationData(subset);
            if (isEmpty(data)) { showEmpty(); return; }
            styleCalibrationChart(chart, data);
            showChart();
        } else if (showSharp) {
            BarData bdata = buildSharpnessData(subset);
            if (isEmpty(bdata)) { showEmpty(); return; }
            styleSharpnessChart(sharpnessChart, bdata);
            showBar();
        } else if (showScore) {
            int windowDays = computeScoreWindowDaysFromFilter();
            LineData sdata = buildScoreSeries(subset, windowDays);
            if (isEmpty(sdata)) { showEmpty(); return; }
            String desc = (scoreMode == ScoreWindowMode.DATE_RANGE)
                    ? "Rolling scores (date-range window)"
                    : "Rolling " + windowDays + "-day Brier / LogLoss";
            styleGenericLineChart(chart, sdata, desc);
            adjustScoreViewport(chart, sdata);
            showChart();
        }
    }

    private void showChart() {
        chart.setVisibility(View.VISIBLE);
        sharpnessChart.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
    }

    private void showBar() {
        chart.setVisibility(View.GONE);
        sharpnessChart.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
    }

    private void showEmpty() {
        chart.setVisibility(View.GONE);
        sharpnessChart.setVisibility(View.GONE);
        empty.setVisibility(View.VISIBLE);
    }

    private boolean isEmpty(Object data) {
        if (data == null) return true;
        if (data instanceof LineData) return ((LineData)data).getDataSetCount() == 0;
        if (data instanceof BarData) return ((BarData)data).getDataSetCount() == 0;
        return true;
    }

    private int computeScoreWindowDaysFromFilter() {
        if (scoreMode == ScoreWindowMode.D7)  return 7;
        if (scoreMode == ScoreWindowMode.D30) return 30;
        if (scoreMode == ScoreWindowMode.D60) return 60;

        if (filter.fromMs != null && filter.toMs != null && filter.toMs >= filter.fromMs) {
            long ms = (filter.toMs - filter.fromMs) + 1L;
            int days = (int) Math.max(1L, Math.round(ms / 86_400_000.0));
            return days;
        }

        return 30;
    }

    private LineData buildScoreSeries(List<Prediction> list, int windowDays) {
        List<Prediction> resolved = new ArrayList<>();
        for (Prediction p : list) if (p.resolved && p.outcomeYes != null) resolved.add(p);
        if (resolved.isEmpty()) return new LineData();

        resolved.sort(java.util.Comparator.comparingLong(p -> p.createdAt));
        long windowMs = windowDays * 24L * 3600L * 1000L;

        ArrayList<Entry> brier = new ArrayList<>();
        ArrayList<Entry> logls = new ArrayList<>();

        for (int i = 0; i < resolved.size(); i++) {
            long tEnd = resolved.get(i).createdAt;
            long tStart = tEnd - windowMs;

            double bSum = 0, lSum = 0; int n = 0;
            for (int j = 0; j <= i; j++) {
                Prediction p = resolved.get(j);
                if (p.createdAt >= tStart && p.createdAt <= tEnd) {
                    double prob = Math.max(0.000001, Math.min(0.999999, p.probability / 100.0));
                    double y = p.outcomeYes ? 1.0 : 0.0;
                    bSum += Math.pow(prob - y, 2);
                    lSum += -(y * Math.log(prob) + (1 - y) * Math.log(1 - prob));
                    n++;
                }
            }
            if (n > 0) {
                brier.add(new Entry(i, (float)(bSum / n)));
                logls.add(new Entry(i, (float)(lSum / n)));
            }
        }

        int c1 = Color.CYAN;
        int c2 = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, Color.LTGRAY);
        LineDataSet dsB = new LineDataSet(brier, "Brier score");
        dsB.setLineWidth(2.5f);
        dsB.setCircleRadius(3f);
        dsB.setColor(c1);
        dsB.setCircleColors(c1);

        LineDataSet dsL = new LineDataSet(logls, "Log loss");
        dsL.setLineWidth(2.5f);
        dsL.setCircleRadius(3f);
        dsL.setColor(c2);
        dsL.setCircleColors(c2);

        return new LineData(dsB, dsL);
    }

    private void adjustScoreViewport(LineChart chart, LineData data) {
        if (data == null || data.getDataSetCount() == 0) return;

        int n = 0;
        for (int i = 0; i < data.getDataSetCount(); i++) {
            n = Math.max(n, data.getDataSetByIndex(i).getEntryCount());
        }
        if (n <= 0) return;

        int visible = Math.min(n, 100);

        XAxis x = chart.getXAxis();
        x.setAxisMinimum(0f);
        x.setAxisMaximum(Math.max(0, n - 1));

        chart.setAutoScaleMinMaxEnabled(true);

        chart.fitScreen();
        chart.setVisibleXRangeMaximum(visible - 1 <= 0 ? 1f : (visible - 1));
        chart.setVisibleXRangeMinimum(visible - 1 <= 0 ? 1f : (visible - 1));

        float targetLeft = Math.max(0, n - visible);
        chart.moveViewToX(targetLeft);

        chart.invalidate();
    }

    private void styleGenericLineChart(LineChart lc, LineData data, String descText) {
        int axis = MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorOnSurface, Color.WHITE);

        Description d = new Description();
        d.setText(descText);
        d.setTextColor(axis);
        lc.setDescription(d);
        lc.setData(data);

        XAxis x = lc.getXAxis();
        YAxis l = lc.getAxisLeft();
        YAxis r = lc.getAxisRight();
        Legend g = lc.getLegend();

        r.setEnabled(false);
        x.setTextColor(axis);
        l.setTextColor(axis);
        g.setTextColor(axis);

        x.setAxisLineColor(axis);
        x.setGridColor(axis & 0x33FFFFFF);
        l.setGridColor(axis & 0x33FFFFFF);

        if (descText != null && descText.toLowerCase(Locale.US).contains("rolling")) {
            float m = Math.max(0.001f, maxY(data));
            float top = (m <= 1f) ? Math.max(1f, m * 1.15f) : Math.min(3f, m * 1.15f);
            l.setAxisMinimum(0f);
            l.setAxisMaximum(top);
            l.setGranularityEnabled(true);
            l.setGranularity(top <= 1f ? 0.1f : 0.2f);
        }

        data.setValueTextColor(axis);
        data.setValueTextSize(11f);
        lc.setExtraTopOffset(12f);
        lc.invalidate();
    }

    private boolean passes(Prediction p) {
        if (filter.fromMs != null && p.createdAt < filter.fromMs) return false;
        if (filter.toMs   != null && p.createdAt > filter.toMs)   return false;

        if (filter.tag != null) {
            if (p.tagLabel == null || !p.tagLabel.equalsIgnoreCase(filter.tag)) return false;
        }

        if (filter.pMin != null && p.probability < filter.pMin) return false;
        if (filter.pMax != null && p.probability > filter.pMax) return false;

        switch (filter.status) {
            case RESOLVED:     return p.resolved;
            case UNRESOLVED:   return !p.resolved;
            case RESOLVED_YES: return p.resolved && p.outcomeYes != null && p.outcomeYes;
            case RESOLVED_NO:  return p.resolved && p.outcomeYes != null && !p.outcomeYes;
            default:           return true;
        }
    }

    private void showFilterDialog() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter_predictions, null, false);

        EditText etFrom = view.findViewById(R.id.etFromDate);
        EditText etTo   = view.findViewById(R.id.etToDate);
        EditText etMin  = view.findViewById(R.id.etProbMin);
        EditText etMax  = view.findViewById(R.id.etProbMax);
        Spinner spTag   = view.findViewById(R.id.spTag);
        Spinner spStatus= view.findViewById(R.id.spStatus);

        if (filter.fromMs != null) {
            etFrom.setText(java.time.LocalDate.ofInstant(
                    java.time.Instant.ofEpochMilli(filter.fromMs),
                    java.time.ZoneId.systemDefault()).toString());
        }
        if (filter.toMs != null) {
            etTo.setText(java.time.LocalDate.ofInstant(
                    java.time.Instant.ofEpochMilli(filter.toMs),
                    java.time.ZoneId.systemDefault()).toString());
        }
        if (filter.pMin != null) etMin.setText(String.valueOf(filter.pMin));
        if (filter.pMax != null) etMax.setText(String.valueOf(filter.pMax));

        etFrom.setOnClickListener(v -> pickDate(etFrom));
        etTo.setOnClickListener(v -> pickDate(etTo));

        List<TagStore.Tag> saved = TagStore.getAll(requireContext());
        List<String> tags = new ArrayList<>();
        tags.add("Any");
        for (TagStore.Tag t : saved) tags.add(t.label);
        ArrayAdapter<String> tagAdapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_item, tags);
        tagAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spTag.setAdapter(tagAdapter);
        if (filter.tag != null) {
            int idx = tags.indexOf(filter.tag);
            if (idx >= 0) spTag.setSelection(idx);
        }

        List<String> statuses = java.util.Arrays.asList(
                "Any","Resolved","Unresolved","Resolved: Yes","Resolved: No"
        );
        ArrayAdapter<String> stAdapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_item, statuses);
        stAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spStatus.setAdapter(stAdapter);
        switch (filter.status) {
            case RESOLVED:     spStatus.setSelection(1); break;
            case UNRESOLVED:   spStatus.setSelection(2); break;
            case RESOLVED_YES: spStatus.setSelection(3); break;
            case RESOLVED_NO:  spStatus.setSelection(4); break;
            default:           spStatus.setSelection(0);
        }

        android.view.ViewGroup rootCol;
        if (view instanceof android.widget.ScrollView) {
            rootCol = (android.view.ViewGroup) ((android.widget.ScrollView) view).getChildAt(0);
        } else {
            rootCol = (android.view.ViewGroup) view;
        }

        TextView tvWin = new TextView(requireContext());
        tvWin.setText("Score window");
        tvWin.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        android.widget.LinearLayout.LayoutParams tvLp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        tvLp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        tvWin.setLayoutParams(tvLp);
        rootCol.addView(tvWin);

        Spinner spWin = new Spinner(requireContext());
        String[] winOpts = new String[]{"7 days", "30 days", "60 days", "Use date range"};
        ArrayAdapter<String> winAdapter =
                new ArrayAdapter<>(requireContext(), R.layout.spinner_item, winOpts);
        winAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spWin.setAdapter(winAdapter);

        int sel = 1;
        switch (scoreMode) {
            case D7: sel = 0; break;
            case D30: sel = 1; break;
            case D60: sel = 2; break;
            case DATE_RANGE: sel = 3; break;
        }
        spWin.setSelection(sel);
        rootCol.addView(spWin);

        TextView tvBin = new TextView(requireContext());
        tvBin.setText("Bin size");
        tvBin.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        android.widget.LinearLayout.LayoutParams tvBinLp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        tvBinLp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        tvBin.setLayoutParams(tvBinLp);
        rootCol.addView(tvBin);

        Spinner spBin = new Spinner(requireContext());
        String[] binOpts = new String[]{"1%", "2%", "5%", "10%", "15%"};
        ArrayAdapter<String> binAdapter =
                new ArrayAdapter<>(requireContext(), R.layout.spinner_item, binOpts);
        binAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spBin.setAdapter(binAdapter);

        int selBin;
        switch (filter.binSize) {
            case 1:  selBin = 0; break;
            case 2:  selBin = 1; break;
            case 5: selBin = 2; break;
            case 10: selBin = 3; break;
            case 15: selBin = 4; break;
            default: selBin = 3;
        }
        spBin.setSelection(selBin);

        rootCol.addView(spBin);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filter (graph)")
                .setView(view)
                .setPositiveButton("Apply", (d,w)->{
                    filter.fromMs = parseDateMs(etFrom.getText());
                    filter.toMs   = parseDateEndMs(etTo.getText());
                    filter.pMin   = parseDouble(etMin.getText());
                    filter.pMax   = parseDouble(etMax.getText());
                    String selTag = (String) spTag.getSelectedItem();
                    filter.tag = "Any".equals(selTag) ? null : selTag;

                    int pos = spStatus.getSelectedItemPosition();
                    switch (pos) {
                        case 1: filter.status = Status.RESOLVED;     break;
                        case 2: filter.status = Status.UNRESOLVED;   break;
                        case 3: filter.status = Status.RESOLVED_YES; break;
                        case 4: filter.status = Status.RESOLVED_NO;  break;
                        default: filter.status = Status.ANY;
                    }
                    int winPos = spWin.getSelectedItemPosition();
                    switch (winPos) {
                        case 0: scoreMode = ScoreWindowMode.D7; break;
                        case 1: scoreMode = ScoreWindowMode.D30; break;
                        case 2: scoreMode = ScoreWindowMode.D60; break;
                        case 3: scoreMode = ScoreWindowMode.DATE_RANGE; break;
                    }

                    int binPos = spBin.getSelectedItemPosition();
                    switch (binPos) {
                        case 0: filter.binSize = 1;  break;
                        case 1: filter.binSize = 2;  break;
                        case 2: filter.binSize = 5; break;
                        case 3: filter.binSize = 10; break;
                        case 4: filter.binSize = 15; break;
                        default: filter.binSize = 10;
                    }

                    chart.clear();
                    sharpnessChart.clear();
                    renderFiltered();
                })
                .setNeutralButton("Clear", (d,w)->{
                    filter.fromMs=filter.toMs=null;
                    filter.pMin=filter.pMax=null;
                    filter.tag=null;
                    filter.status=Status.ANY;
                    filter.binSize = 10;
                    chart.clear();
                    sharpnessChart.clear();
                    renderFiltered();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickDate(EditText target){
        Calendar c = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(requireContext(),
                (view, y,m,d)->target.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dp.show();
    }
    private static @Nullable Long parseDateMs(CharSequence cs){
        if (cs==null||cs.toString().trim().isEmpty()) return null;
        try {
            java.time.LocalDate ld = java.time.LocalDate.parse(cs.toString().trim());
            return ld.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }catch(Exception e){return null;}
    }
    private static @Nullable Long parseDateEndMs(CharSequence cs){
        if (cs==null||cs.toString().trim().isEmpty()) return null;
        try {
            java.time.LocalDate ld = java.time.LocalDate.parse(cs.toString().trim());
            return ld.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()-1;
        }catch(Exception e){return null;}
    }
    private static @Nullable Double parseDouble(CharSequence cs){
        if (cs==null||cs.toString().trim().isEmpty()) return null;
        try { return Double.parseDouble(cs.toString().trim()); }catch(Exception e){return null;}
    }

    private LineData buildCalibrationData(List<Prediction> list) {
        final int binSize = Math.max(1, filter.binSize);
        final int bins = 100 / binSize;

        int[] count = new int[bins];
        int[] yes   = new int[bins];

        for (Prediction p : list) {
            if (!p.resolved || p.outcomeYes == null) continue;
            int prob = (int)Math.max(0, Math.min(100, Math.round(p.probability)));
            int idx = Math.min(bins - 1, prob / binSize);
            count[idx]++; if (p.outcomeYes) yes[idx]++;
        }

        ArrayList<Entry> pts = new ArrayList<>();
        ArrayList<Entry> diag = new ArrayList<>();

        for (int i = 0; i < bins; i++) {
            float binMid = i * binSize + binSize / 2f;
            float observed = (count[i] == 0) ? Float.NaN : (100f * yes[i] / count[i]);
            if (!Float.isNaN(observed)) { pts.add(new Entry(binMid, observed)); }
            diag.add(new Entry(i * binSize, i * binSize));
        }

        int idealColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSecondary, Color.LTGRAY);
        int actualColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, Color.CYAN);

        LineDataSet ideal = new LineDataSet(diag, "Perfect calibration");
        ideal.setDrawCircles(false);
        ideal.setLineWidth(1.5f);
        ideal.setColor(idealColor);

        LineDataSet ds = new LineDataSet(pts, "Actual calibration accuracy (%)");
        ds.setCircleRadius(4f);
        ds.setLineWidth(2.5f);
        ideal.setColor(actualColor);

        ArrayList<ILineDataSet> sets = new ArrayList<>();
        sets.add(ideal);
        sets.add(ds);
        return new LineData(sets);
    }

    private void styleCalibrationChart(LineChart chart, LineData data) {
        int axisTextColor = MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorSecondary,
                Color.WHITE
        );

        Description d = new Description();
        d.setText(getString(R.string.chart_desc));
        chart.setDescription(d);
        chart.setData(data);

        XAxis x = chart.getXAxis();
        YAxis left = chart.getAxisLeft();
        YAxis right = chart.getAxisRight();
        Legend legend = chart.getLegend();

        x.setAxisMinimum(0f); x.setAxisMaximum(100f);
        left.setAxisMinimum(0f); left.setAxisMaximum(100f);
        right.setEnabled(false);

        data.setValueTextColor(axisTextColor);
        data.setValueTextSize(12f);

        x.setTextColor(axisTextColor);
        left.setTextColor(axisTextColor);
        legend.setTextColor(axisTextColor);
        chart.getDescription().setTextColor(axisTextColor);

        x.setAxisLineColor(axisTextColor);
        x.setGridColor(axisTextColor & 0x55FFFFFF);
        left.setGridColor(axisTextColor & 0x55FFFFFF);

        chart.notifyDataSetChanged();
        chart.setExtraTopOffset(12f);
        chart.invalidate();
    }

    private BarData buildSharpnessData(List<Prediction> list) {
        final int binSize = Math.max(1, filter.binSize);
        final int bins = 100 / binSize;
        int[] count = new int[bins];

        for (Prediction p : list) {
            int prob = (int)Math.max(0, Math.min(100, Math.round(p.probability)));
            int idx = Math.min(bins - 1, prob / binSize);
            count[idx]++;
        }

        ArrayList<BarEntry> entries = new ArrayList<>();
        int total = 0;
        for (int c : count) total += c;

        for (int i = 0; i < bins; i++) {
            entries.add(new BarEntry(i, count[i]));
        }

        BarDataSet set = new BarDataSet(entries, "Predictions per probability bin");

        set.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getBarLabel(com.github.mikephil.charting.data.BarEntry entry) {
                float value = entry.getY();
                float total = 0;
                for (com.github.mikephil.charting.data.BarEntry e : entries) total += e.getY();
                float percent = (total > 0) ? (100f * value / total) : 0f;
                return String.format(java.util.Locale.US, "%.0f\n(%.1f%%)", value, percent);
            }
        });

        set.setDrawValues(true);

        return new BarData(set);
    }

    private void styleSharpnessChart(BarChart bar, BarData data) {
        int axisTextColor = MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorSecondary,
                Color.WHITE
        );

        bar.setData(data);

        final int binSize = Math.max(1, filter.binSize);
        final int bins = 100 / binSize;

        String[] labels = new String[bins];
        for (int i = 0; i < bins; i++) {
            int a = i * binSize, b = (i + 1) * binSize;
            labels[i] = a + "–" + b;
        }

        XAxis x = bar.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setLabelCount(bins);
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setTextColor(axisTextColor);
        x.setAxisLineColor(axisTextColor);
        x.setGridColor(axisTextColor & 0x33FFFFFF);

        YAxis left = bar.getAxisLeft();
        YAxis right = bar.getAxisRight();
        left.setTextColor(axisTextColor);
        right.setTextColor(axisTextColor);
        right.setEnabled(false);

        final int total = totalCountFromData(data);
        data.setValueTextColor(axisTextColor);
        data.setValueTextSize(12f);
        data.setValueFormatter(new ValueFormatter() {
            @Override public String getBarLabel(BarEntry barEntry) {
                if (total <= 0) return String.valueOf((int)barEntry.getY());
                float pct = 100f * (barEntry.getY() / total);
                return String.format(Locale.US, "%d (%.0f%%)", (int)barEntry.getY(), pct);
            }
        });

        Legend legend = bar.getLegend();
        legend.setTextColor(axisTextColor);

        Description d = new Description();
        d.setText("Frequency: how often each probability range is used");
        d.setTextColor(axisTextColor);
        bar.setDescription(d);

        bar.setFitBars(true);
        bar.invalidate();
    }

    private int totalCountFromData(BarData data) {
        if (data == null || data.getDataSetCount() == 0) return 0;
        int total = 0;
        for (int i = 0; i < data.getDataSetCount(); i++) {
            BarDataSet ds = (BarDataSet) data.getDataSetByIndex(i);
            for (int j = 0; j < ds.getEntryCount(); j++) {
                total += (int) ds.getEntryForIndex(j).getY();
            }
        }
        return total;
    }

    private float maxY(LineData data) {
        float m = 0f;
        if (data == null) return m;
        for (int i = 0; i < data.getDataSetCount(); i++) {
            for (int j = 0; j < data.getDataSetByIndex(i).getEntryCount(); j++) {
                float y = data.getDataSetByIndex(i).getEntryForIndex(j).getY();
                if (y > m) m = y;
            }
        }
        return m;
    }
}