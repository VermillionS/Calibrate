package com.example.calibrate.ui;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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
import androidx.preference.PreferenceManager;

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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

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

    private static final String PREF_FILTER = "graph_filter";

    enum ScoreType { BRIER, LOG }
    private ScoreType scoreType = ScoreType.BRIER;

    @Override public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        chart = root.findViewById(R.id.chart);
        sharpnessChart = root.findViewById(R.id.sharpnessChart);
        empty = root.findViewById(R.id.tvEmpty);
        toggle = root.findViewById(R.id.toggleGroup);

        vm = new ViewModelProvider(requireActivity()).get(PredictionViewModel.class);

        loadFilter();
        updateFilterIcon();

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
            LineData sdata = buildScoreSeries(subset);
            if (isEmpty(sdata)) { showEmpty(); return; }
            styleGenericLineChart(chart, sdata, " ");
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

    private LineData buildScoreSeries(List<Prediction> list) {
        List<Prediction> resolved = new ArrayList<>();
        for (Prediction p : list)
            if (p.resolved && p.outcomeYes != null)
                resolved.add(p);

        if (resolved.isEmpty()) return new LineData();

        resolved.sort(java.util.Comparator.comparingLong(p -> p.createdAt));

        ArrayList<Entry> rolling = new ArrayList<>();
        ArrayList<Entry> instant = new ArrayList<>();

        double sum = 0;
        for (int i = 0; i < resolved.size(); i++) {
            Prediction pi = resolved.get(i);
            double prob = Math.max(0.000001, Math.min(0.999999, pi.probability / 100.0));
            double y = pi.outcomeYes ? 1.0 : 0.0;

            double value = (scoreType == ScoreType.LOG)
                    ? -(y * Math.log(prob) + (1 - y) * Math.log(1 - prob))
                    : Math.pow(prob - y, 2);

            instant.add(new Entry(i, (float) value, pi));

            sum += value;
            double avg = sum / (i + 1);
            rolling.add(new Entry(i, (float) avg));
        }

        int cInst = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiary, Color.CYAN);
        int cRoll = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, Color.MAGENTA);

        String labelRoll = (scoreType == ScoreType.LOG)
                ? "Log loss (rolling average)"
                : "Brier Score (rolling average)";
        String labelInst = (scoreType == ScoreType.LOG)
                ? "Log loss (individual prediction)"
                : "Brier Score (individual prediction)";

        LineDataSet dsInst = new LineDataSet(instant, labelInst);
        dsInst.setLineWidth(2f);
        dsInst.setCircleRadius(3f);
        dsInst.setColor(cInst);
        dsInst.setCircleColors(cInst);

        LineDataSet dsRoll = new LineDataSet(rolling, labelRoll);
        dsRoll.setLineWidth(2.5f);
        dsRoll.setCircleRadius(3f);
        dsRoll.setColor(cRoll);
        dsRoll.setCircleColors(cRoll);

        return new LineData(dsRoll, dsInst);
    }

    private void adjustScoreViewport(LineChart chart, LineData data) {
        if (data == null || data.getDataSetCount() == 0) return;

        int n = 0;
        for (int i = 0; i < data.getDataSetCount(); i++) {
            n = Math.max(n, data.getDataSetByIndex(i).getEntryCount());
        }
        if (n <= 0) return;

        int defaultVisible = Math.min(n, 100);

        XAxis x = chart.getXAxis();
        x.setAxisMinimum(0f);
        x.setAxisMaximum(Math.max(0, n - 1));

        chart.setAutoScaleMinMaxEnabled(true);
        chart.fitScreen();
        if (defaultVisible > 1) {
            chart.setVisibleXRangeMaximum(defaultVisible - 1);

        }

        float targetLeft = Math.max(0, n - defaultVisible);
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

        if (descText != null) {
            float m = Math.max(0.001f, maxY(data));
            float top = (m <= 1f) ? Math.max(1f, m * 1.15f) : Math.min(10f, m * 1.15f);
            l.setAxisMinimum(-0.05f);
            l.setAxisMaximum(top);
            l.setGranularityEnabled(true);
            l.setGranularity(top <= 1f ? 0.1f : 0.2f);
        }

        data.setValueTextColor(axis);
        data.setValueTextSize(11f);
        lc.setExtraTopOffset(12f);
        lc.invalidate();

        chart.setHighlightPerTapEnabled(true);
        chart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, com.github.mikephil.charting.highlight.Highlight h) {
                Object tag = e.getData();
                if (tag instanceof Prediction) {
                    Prediction p = (Prediction) tag;

                    StringBuilder msg = new StringBuilder();
                    msg.append(String.format(
                            java.util.Locale.US,
                            "p=%.2f%%\n%s\nResolved: %s",
                            p.probability,
                            java.text.DateFormat.getDateTimeInstance().format(p.createdAt),
                            p.resolved ? (p.outcomeYes != null && p.outcomeYes ? "Yes" : "No") : "Unresolved"
                    ));

                    if (p.tagLabel != null && !p.tagLabel.trim().isEmpty()
                            && !"No tag".equalsIgnoreCase(p.tagLabel.trim())) {
                        msg.append("\nTag: ").append(p.tagLabel);
                    }
                    if (p.description != null && !p.description.trim().isEmpty()) {
                        msg.append("\n\n").append(p.description.trim());
                    }

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(p.title)
                            .setMessage(msg.toString())
                            .setPositiveButton("OK", null)
                            .show();
                }
            }
            @Override public void onNothingSelected() {}
        });
        chart.setMaxHighlightDistance(20f);
    }

    private boolean passes(Prediction p) {
        if (filter.fromMs != null && p.createdAt < filter.fromMs) return false;
        if (filter.toMs   != null && p.createdAt > filter.toMs)   return false;

        if (filter.tag != null && !filter.tag.trim().isEmpty()) {
            String[] parts = filter.tag.split(Pattern.quote("||||"));
            boolean wantsNoTag = false;
            List<String> wants = new ArrayList<>();
            for (String s : parts) {
                String t = s == null ? "" : s.trim();
                if (t.isEmpty()) continue;
                if (t.equalsIgnoreCase("No tag")) wantsNoTag = true;
                else wants.add(t);
            }

            String pt = (p.tagLabel == null || p.tagLabel.trim().isEmpty()) ? null : p.tagLabel;
            boolean match = false;
            if (pt == null) {
                match = wantsNoTag;
            } else {
                for (String w : wants) {
                    if (w.equalsIgnoreCase(pt)) { match = true; break; }
                }
            }
            if (!match) return false;
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
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupTags);
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

        chipGroup.setSingleSelection(false);
        chipGroup.setSelectionRequired(false);

        Set<String> selected = new HashSet<>();
        if (filter.tag != null && !filter.tag.trim().isEmpty()) {
            for (String s : filter.tag.split(java.util.regex.Pattern.quote("||||"))) {
                if (!s.trim().isEmpty()) selected.add(s.trim());
            }
        }

        chipGroup.removeAllViews();

        {
            Chip chip = new Chip(requireContext(), null,
                    com.google.android.material.R.style.Widget_Material3_Chip_Filter);
            chip.setText("No tag");
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setChecked(selected.contains("No tag"));
            chip.setEnsureMinTouchTargetSize(true);
            chip.setCheckedIconVisible(true);
            chip.setChipBackgroundColor(chipBgColors(requireContext()));
            chip.setTextColor(MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorOnSurface,
                    0xFF000000));
            chipGroup.addView(chip);
        }

        List<TagStore.Tag> allTags = TagStore.getAll(requireContext());
        for (TagStore.Tag t : allTags) {
            Chip chip = new Chip(requireContext(), null,
                    com.google.android.material.R.style.Widget_Material3_Chip_Filter);
            chip.setText(t.label);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setChecked(selected.contains(t.label));
            chip.setEnsureMinTouchTargetSize(true);
            chip.setCheckedIconVisible(true);
            chip.setChipBackgroundColor(chipBgColors(requireContext()));
            chip.setTextColor(MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorOnSurface,
                    0xFF000000));
            chipGroup.addView(chip);
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
        String[] binOpts = new String[]{"1%", "2%", "5%", "10%", "20%"};
        ArrayAdapter<String> binAdapter =
                new ArrayAdapter<>(requireContext(), R.layout.spinner_item, binOpts);
        binAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spBin.setAdapter(binAdapter);

        int selBin;
        switch (filter.binSize) {
            case 1:  selBin = 0; break;
            case 2:  selBin = 1; break;
            case 5:  selBin = 2; break;
            case 10: selBin = 3; break;
            case 20: selBin = 4; break;
            default: selBin = 3;
        }
        spBin.setSelection(selBin);
        rootCol.addView(spBin);

        TextView tvType = new TextView(requireContext());
        tvType.setText("Score type");
        tvType.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        android.widget.LinearLayout.LayoutParams tvTypeLp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        tvTypeLp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        tvType.setLayoutParams(tvTypeLp);
        rootCol.addView(tvType);

        Spinner spType = new Spinner(requireContext());
        String[] typeOpts = new String[]{"Brier", "Log loss"};
        ArrayAdapter<String> typeAdapter =
                new ArrayAdapter<>(requireContext(), R.layout.spinner_item, typeOpts);
        typeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spType.setAdapter(typeAdapter);
        spType.setSelection(scoreType == ScoreType.LOG ? 1 : 0);
        rootCol.addView(spType);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filter (graph)")
                .setView(view)
                .setPositiveButton("Apply", (d,w)->{
                    filter.fromMs = parseDateMs(etFrom.getText());
                    filter.toMs   = parseDateEndMs(etTo.getText());
                    filter.pMin   = parseDouble(etMin.getText());
                    filter.pMax   = parseDouble(etMax.getText());
                    List<String> sel = new ArrayList<>();
                    for (int i = 0; i < chipGroup.getChildCount(); i++) {
                        Chip c = (Chip) chipGroup.getChildAt(i);
                        if (c.isChecked()) sel.add(c.getText().toString());
                    }
                    filter.tag = sel.isEmpty() ? null : String.join("||||", sel);
                    scoreType = (spType.getSelectedItemPosition() == 1) ? ScoreType.LOG : ScoreType.BRIER;

                    int pos = spStatus.getSelectedItemPosition();
                    switch (pos) {
                        case 1: filter.status = Status.RESOLVED;     break;
                        case 2: filter.status = Status.UNRESOLVED;   break;
                        case 3: filter.status = Status.RESOLVED_YES; break;
                        case 4: filter.status = Status.RESOLVED_NO;  break;
                        default: filter.status = Status.ANY;
                    }

                    int binPos = spBin.getSelectedItemPosition();
                    switch (binPos) {
                        case 0: filter.binSize = 1;  break;
                        case 1: filter.binSize = 2;  break;
                        case 2: filter.binSize = 5;  break;
                        case 3: filter.binSize = 10; break;
                        case 4: filter.binSize = 20; break;
                        default: filter.binSize = 10;
                    }

                    chart.clear();
                    sharpnessChart.clear();
                    renderFiltered();
                    saveFilter();
                    updateFilterIcon();
                })
                .setNeutralButton("Clear", (d,w)->{
                    filter.fromMs=filter.toMs=null;
                    filter.pMin=filter.pMax=null;
                    filter.tag=null;
                    filter.status=Status.ANY;
                    filter.binSize = 10;
                    scoreType = ScoreType.BRIER;
                    chart.clear();
                    sharpnessChart.clear();
                    renderFiltered();
                    saveFilter();
                    updateFilterIcon();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static android.content.res.ColorStateList chipBgColors(@NonNull android.content.Context ctx) {
        int checked = com.google.android.material.color.MaterialColors.getColor(
                ctx, com.google.android.material.R.attr.colorPrimary, 0xFFE0E0E0);
        int unchecked = com.google.android.material.color.MaterialColors.getColor(
                ctx, com.google.android.material.R.attr.colorSurfaceVariant, 0xFFF2F2F2);

        return new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}},
                new int[]{checked, unchecked}
        );
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
            float xAtBin = i * binSize;
            float observed = (count[i] == 0) ? Float.NaN : (100f * yes[i] / count[i]);
            if (!Float.isNaN(observed)) { pts.add(new Entry(xAtBin, observed)); }
            diag.add(new Entry(xAtBin, xAtBin));
        }

        int idealColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiary, Color.CYAN);
        int actualColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, Color.RED);

        LineDataSet ideal = new LineDataSet(diag, "Perfect calibration");
        ideal.setDrawCircles(false);
        ideal.setLineWidth(1.5f);
        ideal.setColor(idealColor);

        LineDataSet ds = new LineDataSet(pts, "Actual calibration accuracy (%)");
        ds.setCircleRadius(4f);
        ds.setLineWidth(2.5f);
        ds.setColor(actualColor);
        ds.setCircleColors(actualColor);

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
        d.setText(" ");
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

        final class AdaptiveValueFormatter extends ValueFormatter {
            boolean showValues = true;
            boolean showPercent = true;

            @Override public String getBarLabel(BarEntry e) {
                if (!showValues) return "";
                int count = (int) e.getY();
                if (total <= 0) return String.valueOf(count);
                float pct = 100f * (e.getY() / total);
                return showPercent ? String.format(Locale.US, "%d (%.0f%%)", count, pct)
                        : String.format(Locale.US, "%d", count);
            }
        }
        final AdaptiveValueFormatter vf = new AdaptiveValueFormatter();

        data.setValueTextColor(axisTextColor);
        data.setValueTextSize(10f);
        for (int i = 0; i < data.getDataSetCount(); i++) {
            data.getDataSetByIndex(i).setValueFormatter(vf);
        }

        Legend legend = bar.getLegend();
        legend.setTextColor(axisTextColor);

        Description d = new Description();
        d.setText(" ");
        d.setTextColor(axisTextColor);
        bar.setDescription(d);
        bar.setFitBars(true);
        bar.setExtraTopOffset(10f);

        final Runnable applyAdaptive = () -> {
            float scaleX = bar.getViewPortHandler().getScaleX();
            boolean tooDense = (bins > 40 && scaleX < 2f) || (bins > 20 && scaleX < 1.3f);

            vf.showValues = !tooDense;
            if (!vf.showValues) {
                vf.showPercent = false;
            } else if (scaleX < 1.9f) {
                vf.showPercent = false;
            } else {
                vf.showPercent = true;
            }

            for (int i = 0; i < data.getDataSetCount(); i++) {
                data.getDataSetByIndex(i).setDrawValues(vf.showValues);
            }
            bar.invalidate();
        };

        applyAdaptive.run();
        bar.setOnChartGestureListener(new com.github.mikephil.charting.listener.OnChartGestureListener() {
            @Override public void onChartScale(android.view.MotionEvent me, float scaleX, float scaleY) { applyAdaptive.run(); }
            @Override public void onChartGestureStart(android.view.MotionEvent me, com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture lastPerformedGesture) {}
            @Override public void onChartGestureEnd(android.view.MotionEvent me, com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture lastPerformedGesture) {}
            @Override public void onChartLongPressed(android.view.MotionEvent me) {}
            @Override public void onChartDoubleTapped(android.view.MotionEvent me) {}
            @Override public void onChartSingleTapped(android.view.MotionEvent me) {}
            @Override public void onChartFling(android.view.MotionEvent me1, android.view.MotionEvent me2, float velocityX, float velocityY) {}
            @Override public void onChartTranslate(android.view.MotionEvent me, float dX, float dY) {}
        });

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

    private void saveFilter() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());
        sp.edit()
                .putString(PREF_FILTER + "_tag", filter.tag)
                .putString(PREF_FILTER + "_status", filter.status.name())
                .putString(PREF_FILTER + "_pMin", filter.pMin == null ? "" : filter.pMin.toString())
                .putString(PREF_FILTER + "_pMax", filter.pMax == null ? "" : filter.pMax.toString())
                .putLong(PREF_FILTER + "_from", filter.fromMs == null ? -1 : filter.fromMs)
                .putLong(PREF_FILTER + "_to", filter.toMs == null ? -1 : filter.toMs)
                .putInt(PREF_FILTER + "_binSize", filter.binSize)
                .putString(PREF_FILTER + "_scoreType", scoreType.name())
                .apply();
    }

    private void loadFilter() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());
        filter.tag = sp.getString(PREF_FILTER + "_tag", null);
        try { filter.status = Status.valueOf(sp.getString(PREF_FILTER + "_status", Status.ANY.name())); }
        catch (Exception e) { filter.status = Status.ANY; }

        String pMin = sp.getString(PREF_FILTER + "_pMin", "");
        String pMax = sp.getString(PREF_FILTER + "_pMax", "");
        filter.pMin = pMin.isEmpty() ? null : Double.parseDouble(pMin);
        filter.pMax = pMax.isEmpty() ? null : Double.parseDouble(pMax);

        long from = sp.getLong(PREF_FILTER + "_from", -1);
        long to = sp.getLong(PREF_FILTER + "_to", -1);
        filter.fromMs = (from == -1 ? null : from);
        filter.toMs = (to == -1 ? null : to);

        filter.binSize = sp.getInt(PREF_FILTER + "_binSize", 10);
        try {
            scoreType = ScoreType.valueOf(sp.getString(PREF_FILTER + "_scoreType", ScoreType.BRIER.name()));
        } catch (Exception e)
        { scoreType = ScoreType.BRIER; }
    }

    private void updateFilterIcon() {
        View btnFilter = getView().findViewById(R.id.btnFilter);
        if (!(btnFilter instanceof com.google.android.material.button.MaterialButton)) return;
        MaterialButton mb = (MaterialButton) btnFilter;

        boolean active = (filter.tag != null || filter.fromMs != null || filter.toMs != null
                || filter.pMin != null || filter.pMax != null || filter.status != Status.ANY
                || filter.binSize != 10 || scoreType != ScoreType.BRIER);

        int colorAttr = active
                ? com.google.android.material.R.attr.colorOnSurface
                : com.google.android.material.R.attr.colorSurface;

        int tint = MaterialColors.getColor(requireContext(), colorAttr, 0xFFFFFFFF);
        mb.setIconTint(ColorStateList.valueOf(tint));
    }

    @Override public void onResume() {
        super.onResume();
        loadFilter();
        updateFilterIcon();
        renderFiltered();
    }
}