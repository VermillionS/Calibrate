package com.example.calibrate.ui;

import com.example.calibrate.R;
import androidx.fragment.app.Fragment;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import androidx.appcompat.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.calibrate.data.Prediction;
import com.example.calibrate.data.TagStore;
import com.example.calibrate.vm.PredictionViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import java.util.List;
import java.time.*;
import java.util.*;

public class HomeFragment extends Fragment {
    private PredictionViewModel vm;
    private Adapter adapter;
    private List<Prediction> all = Collections.emptyList();
    private final FilterState filter = new FilterState();
    private SortMode sortMode = SortMode.DATE_DESC; // default newest first
    enum SortMode {
        DATE_DESC, DATE_ASC,
        PROB_DESC, PROB_ASC
    }

    public HomeFragment() { super(R.layout.frag_home); }

    static class FilterState {
        @Nullable Long fromMs, toMs;
        @Nullable String tag;
        @Nullable Double pMin, pMax;
        Status status = Status.ANY;
    }
    enum Status { ANY, RESOLVED, UNRESOLVED, RESOLVED_YES, RESOLVED_NO }

    @Override public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        vm = new ViewModelProvider(requireActivity()).get(PredictionViewModel.class);

        RecyclerView rv = root.findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new Adapter(vm);
        rv.setAdapter(adapter);

        vm.predictions().observe(getViewLifecycleOwner(), list -> {
            all = (list == null) ? Collections.emptyList() : list;
            applyFilter();
        });

        root.findViewById(R.id.fabAdd).setOnClickListener(v -> showAddDialog());

        View btnFilter = root.findViewById(R.id.btnFilter);
        if (btnFilter != null) btnFilter.setOnClickListener(v -> showFilterDialog());

        View btnSort = root.findViewById(R.id.btnSort);
        if (btnSort != null) btnSort.setOnClickListener(v -> showSortDialog());

        SearchView searchView = root.findViewById(R.id.searchView);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return true;
            }
        });
    }

    private void showSortDialog() {
        final String[] labels = new String[] {
                "Date: Newest → Oldest",
                "Date: Oldest → Newest",
                "Probability: High → Low",
                "Probability: Low → High"
        };
        int checked = 0;
        switch (sortMode) {
            case DATE_DESC: checked = 0; break;
            case DATE_ASC:  checked = 1; break;
            case PROB_DESC: checked = 2; break;
            case PROB_ASC:  checked = 3; break;
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sort by")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    switch (which) {
                        case 0: sortMode = SortMode.DATE_DESC; break;
                        case 1: sortMode = SortMode.DATE_ASC;  break;
                        case 2: sortMode = SortMode.PROB_DESC; break;
                        case 3: sortMode = SortMode.PROB_ASC;  break;
                    }
                })
                .setPositiveButton("Apply", (d,w) -> applyFilter())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyFilter() {
        List<Prediction> out = new ArrayList<>();
        for (Prediction p : all) if (passes(p)) out.add(p);

        // --- NEW: apply sort
        switch (sortMode) {
            case DATE_DESC:
                Collections.sort(out, (a,b) -> Long.compare(b.createdAt, a.createdAt));
                break;
            case DATE_ASC:
                Collections.sort(out, (a,b) -> Long.compare(a.createdAt, b.createdAt));
                break;
            case PROB_DESC:
                Collections.sort(out, (a,b) -> Double.compare(b.probability, a.probability));
                break;
            case PROB_ASC:
                Collections.sort(out, (a,b) -> Double.compare(a.probability, b.probability));
                break;
        }

        adapter.submit(out);
    }

    @Override public void onResume() {
        super.onResume();
        applyFilter();
    }

    private boolean passes(Prediction p) {
        if (filter.fromMs != null && p.createdAt < filter.fromMs) return false;
        if (filter.toMs != null && p.createdAt > filter.toMs) return false;
        if (filter.tag != null) {
            if (p.tagLabel == null || !p.tagLabel.equalsIgnoreCase(filter.tag)) return false;
        }
        if (filter.pMin != null && p.probability < filter.pMin) return false;
        if (filter.pMax != null && p.probability > filter.pMax) return false;
        if (filter.status == Status.RESOLVED && !p.resolved) return false;
        if (filter.status == Status.UNRESOLVED && p.resolved) return false;
        if (filter.status == Status.RESOLVED_YES && (!p.resolved || p.outcomeYes == null || !p.outcomeYes))
            return false;
        if (filter.status == Status.RESOLVED_NO && (!p.resolved || p.outcomeYes == null || p.outcomeYes))
            return false;
        return true;
    }

    private void showFilterDialog() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter_predictions, null, false);

        EditText etFrom = view.findViewById(R.id.etFromDate);
        EditText etTo   = view.findViewById(R.id.etToDate);
        EditText etMin  = view.findViewById(R.id.etProbMin);
        EditText etMax  = view.findViewById(R.id.etProbMax);
        Spinner spTag   = view.findViewById(R.id.spTag);
        Spinner spStatus= view.findViewById(R.id.spStatus);

        if (filter.fromMs != null)
            etFrom.setText(java.time.LocalDate.ofInstant(
                    java.time.Instant.ofEpochMilli(filter.fromMs),
                    java.time.ZoneId.systemDefault()
            ).toString());

        if (filter.toMs != null)
            etTo.setText(java.time.LocalDate.ofInstant(
                    java.time.Instant.ofEpochMilli(filter.toMs),
                    java.time.ZoneId.systemDefault()
            ).toString());

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

        List<String> statuses = Arrays.asList("Any","Resolved","Unresolved","Resolved (Yes)","Resolved (No)");
        ArrayAdapter<String> stAdapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_item, statuses);
        stAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spStatus.setAdapter(stAdapter);

        switch (filter.status) {
            case RESOLVED: spStatus.setSelection(1); break;
            case UNRESOLVED: spStatus.setSelection(2); break;
            case RESOLVED_YES: spStatus.setSelection(3); break;
            case RESOLVED_NO: spStatus.setSelection(4); break;
            default: spStatus.setSelection(0);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filter predictions")
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
                        case 1: filter.status = Status.RESOLVED; break;
                        case 2: filter.status = Status.UNRESOLVED; break;
                        case 3: filter.status = Status.RESOLVED_YES; break;
                        case 4: filter.status = Status.RESOLVED_NO; break;
                        default: filter.status = Status.ANY;
                    }
                    applyFilter();
                })
                .setNeutralButton("Clear", (d,w)->{
                    filter.fromMs=filter.toMs=null;
                    filter.pMin=filter.pMax=null;
                    filter.tag=null;
                    filter.status=Status.ANY;
                    applyFilter();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickDate(EditText target){
        Calendar c = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(requireContext(),
                (view, y,m,d)->target.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)),
                c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH));
        dp.show();
    }

    private static @Nullable Long parseDateMs(CharSequence cs){
        if (cs==null||cs.toString().trim().isEmpty()) return null;
        try {
            LocalDate ld = LocalDate.parse(cs.toString().trim());
            return ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }catch(Exception e){return null;}
    }

    private static @Nullable Long parseDateEndMs(CharSequence cs){
        if (cs==null||cs.toString().trim().isEmpty()) return null;
        try {
            LocalDate ld = LocalDate.parse(cs.toString().trim());
            return ld.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()-1;
        }catch(Exception e){return null;}
    }

    private static @Nullable Double parseDouble(CharSequence cs){
        if (cs==null||cs.toString().trim().isEmpty()) return null;
        try { return Double.parseDouble(cs.toString().trim()); }catch(Exception e){return null;}
    }

    private static class TagOpt {
        final String label; final int color; TagOpt(String l, int c){ label=l;color=c; }
        @Override public String toString(){ return label; }
    }

    private void showAddDialog() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_prediction, null, false);
        EditText etTitle = view.findViewById(R.id.etTitle);
        EditText etProb  = view.findViewById(R.id.etProb);
        Spinner  spTag   = view.findViewById(R.id.spinnerTag);
        View     vSwatch = view.findViewById(R.id.viewTagColor);
        Slider slider = view.findViewById(R.id.sliderProb);
        EditText etDesc = view.findViewById(R.id.etDesc);

        final int NO_COLOR = 0;
        final boolean[] fromSlider = { false };
        final boolean[] fromText   = { false };

        Double initial = parseProb(String.valueOf(etProb.getText()));
        float startVal = initial == null ? 50f : (float) clampPct(initial);
        slider.setValue(startVal);

        slider.setLabelFormatter(value ->
                String.format(java.util.Locale.US, "%.1f%%", value)
        );

        slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromText[0]) return;
            fromSlider[0] = true;
            etProb.setText(String.format(java.util.Locale.US, "%.1f", value));
            etProb.setSelection(etProb.getText().length());
            fromSlider[0] = false;
        });

        etProb.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (fromSlider[0]) return;
                fromText[0] = true;
                Double v = parseProb(s == null ? "" : s.toString());
                float clamped = (float) (v == null ? slider.getValue() : clampPct(v));
                if (Math.abs(slider.getValue() - clamped) > 0.0001f) slider.setValue(clamped);
                fromText[0] = false;
            }
        });

        java.util.List<TagStore.Tag> saved = TagStore.getAll(requireContext());
        java.util.ArrayList<TagOpt> opts = new java.util.ArrayList<>();
        opts.add(new TagOpt("No tag", NO_COLOR));
        for (TagStore.Tag t : saved) opts.add(new TagOpt(t.label, t.color));
        opts.add(new TagOpt("New tag...", -1));

        ArrayAdapter<TagOpt> adapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_item, opts);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spTag.setAdapter(adapter);
        spTag.setSelection(0);
        vSwatch.setBackgroundColor(0x00000000);

        final int[] lastRealIndex = { 0 };
        final TagOpt[] lastRealTag = { null };

        spTag.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                TagOpt t = (TagOpt) parent.getItemAtPosition(pos);

                if (t.color == -1) {
                    vSwatch.setBackgroundColor(0x00000000);

                    final EditText etName = new EditText(requireContext());
                    etName.setHint("Tag name");
                    final EditText etHex  = new EditText(requireContext());
                    etHex.setHint("#RRGGBB or #AARRGGBB");

                    LinearLayout ll = new LinearLayout(requireContext());
                    ll.setOrientation(LinearLayout.VERTICAL);
                    ll.setPadding(24, 16, 24, 0);
                    ll.addView(etName);

                    final int[] pickedColor = { PALETTE_16[0] };

                    View paletteGrid = buildColorGrid(ll, PALETTE_16, pickedColor[0], c -> {
                        pickedColor[0] = c;
                        etHex.setText(String.format(java.util.Locale.US, "#%08X", c));
                    });
                    ll.addView(paletteGrid);
                    // ll.addView(etHex); shows hex code entry

                    etHex.setOnFocusChangeListener((view, hasFocus) -> {
                        if (!hasFocus) {
                            int c = parseColorOr(etHex.getText().toString(), pickedColor[0]);
                            pickedColor[0] = c;
                        }
                    });

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Calibrate_Dialog)
                            .setTitle("New tag")
                            .setView(ll)
                            .setPositiveButton("OK", (d,w)->{
                                String name = etName.getText().toString().trim();
                                if (!name.isEmpty()) {
                                    int color = parseColorOr(etHex.getText().toString(), pickedColor[0]);

                                    TagStore.addOrUpdate(requireContext(), new TagStore.Tag(name, color));
                                    vm.renameTagEverywhere(name, name, color);
                                    opts.add(opts.size() - 1, new TagOpt(name, color));
                                    adapter.notifyDataSetChanged();

                                    int newIndex = opts.size() - 2;
                                    spTag.setSelection(newIndex);
                                    vSwatch.setBackgroundColor(color);

                                    lastRealIndex[0] = newIndex;
                                    lastRealTag[0]   = new TagOpt(name, color);
                                } else {
                                    toast("Must enter a new Tag's Name");
                                    int idx = (lastRealIndex[0] < 0) ? 0 : lastRealIndex[0];
                                    spTag.setSelection(idx);
                                    TagOpt prev = (TagOpt) spTag.getSelectedItem();
                                    vSwatch.setBackgroundColor(prev == null || prev.color == 0 ? 0x00000000 : prev.color);
                                }
                            })
                            .setNegativeButton("Cancel", (d,w)->{
                                int idx = (lastRealIndex[0] < 0) ? 0 : lastRealIndex[0];
                                spTag.setSelection(idx);
                                TagOpt prev = (TagOpt) spTag.getSelectedItem();
                                vSwatch.setBackgroundColor(prev == null || prev.color == 0 ? 0x00000000 : prev.color);
                            })
                            .show();

                } else {
                    vSwatch.setBackgroundColor(t.color == 0 ? 0x00000000 : t.color);

                    if (t.color != 0) {
                        lastRealIndex[0] = pos;
                        lastRealTag[0]   = t;
                    }
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        androidx.appcompat.app.AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("New Prediction")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {
                    String rawTitle = String.valueOf(etTitle.getText());
                    String title = sanitizeTitle(rawTitle);
                    if (title.isEmpty()) { toast("Enter a title"); return; }

                    Double prob = parseProb(String.valueOf(etProb.getText()));
                    if (prob == null) { toast("Enter a probability"); return; }
                    if (prob <= 0) { toast("Probability must be greater than 0%"); return; }
                    if (prob >= 100) { toast("Probability must be less than 100%"); return; }
                    double p = clampPct(prob.doubleValue());

                    TagOpt sel = (TagOpt) spTag.getSelectedItem();
                    String tagLabel = (sel == null || sel.color == -1 || "No tag".equals(sel.label)) ? null : sel.label;
                    int tagColor = (sel == null || sel.color == 0) ? 0 : sel.color;

                    String desc = String.valueOf(etDesc.getText()).trim();
                    if (desc.isEmpty()) desc = null;

                    vm.addPrediction(title, p, desc, tagLabel, tagColor);
                })
                .setNegativeButton("Cancel", null)
                .create() ;
        dialog.show();
    }

    private static String sanitizeTitle(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\p{Cntrl}", "");  // strip control chars
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > 200) s = s.substring(0, 200);
        return s;
    }

    private static Double parseProb(String s) {
        try { String t = s==null ? "" : s.trim(); if (t.isEmpty()) return null; return Double.parseDouble(t); }
        catch (Exception e) { return null; }
    }
    private static double clampPct(double v) { return v < 0.0 ? 0.0 : (v > 100.0 ? 100.0 : v); }
    private void toast(String s){ android.widget.Toast.makeText(requireContext(), s, android.widget.Toast.LENGTH_SHORT).show(); }
    private static int parseColorOr(String hex, int fallback) {
        try { String t = hex.trim(); if (!t.startsWith("#")) t = "#"+t; return android.graphics.Color.parseColor(t); }
        catch (Exception e) { return fallback; }
    }

    private static final int[] PALETTE_16 = new int[]{
            0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1,
            0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1,
            0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFFDD835,
            0xFFFFB300, 0xFFFB8C00, 0xFFF4511E, 0xFF6D4C41
    };

    private static int dp(View v, int dps) {
        float den = v.getResources().getDisplayMetrics().density;
        return Math.round(dps * den);
    }

    private static View buildColorGrid(View anchorForDp, int[] palette, int initiallySelectedColor,
                                       java.util.function.IntConsumer onPick) {
        android.widget.GridLayout grid = new android.widget.GridLayout(anchorForDp.getContext());
        grid.setColumnCount(4);
        grid.setRowCount((int) Math.ceil(palette.length / 4.0));
        grid.setPadding(0, dp(anchorForDp, 8), 0, 0);

        final View[] selected = new View[1];

        for (int color : palette) {
            View swatch = new View(anchorForDp.getContext());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            int size = dp(anchorForDp, 36);
            lp.width = size; lp.height = size;
            lp.setMargins(dp(anchorForDp, 6), dp(anchorForDp, 6), dp(anchorForDp, 6), dp(anchorForDp, 6));
            swatch.setLayoutParams(lp);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(anchorForDp, 18));
            bg.setColor(color);

            if (color == initiallySelectedColor) {
                bg.setStroke(dp(anchorForDp, 2), 0xFFFFFFFF);
                selected[0] = swatch;
            } else {
                bg.setStroke(dp(anchorForDp, 2), 0x00000000);
            }
            swatch.setBackground(bg);

            swatch.setOnClickListener(v -> {

                if (selected[0] != null && selected[0].getBackground() instanceof android.graphics.drawable.GradientDrawable) {
                    ((android.graphics.drawable.GradientDrawable) selected[0].getBackground())
                            .setStroke(dp(anchorForDp, 2), 0x00000000);
                    selected[0].invalidate();
                }

                ((android.graphics.drawable.GradientDrawable) v.getBackground())
                        .setStroke(dp(anchorForDp, 2), 0xFFFFFFFF);
                v.invalidate();
                selected[0] = v;

                onPick.accept(color);
            });

            grid.addView(swatch);
        }
        return grid;
    }

    static class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private List<Prediction> data = new java.util.ArrayList<>();
        private List<Prediction> full = new java.util.ArrayList<>();
        private final PredictionViewModel vm;
        Adapter(PredictionViewModel vm) { this.vm = vm; }
        void submit(List<Prediction> list) {
            full = new java.util.ArrayList<>(list);
            data = new java.util.ArrayList<>(list);
            notifyDataSetChanged();
        }

        void filter(String text) {
            if (text == null || text.trim().isEmpty()) {
                data = new java.util.ArrayList<>(full);
            } else {
                String lower = text.toLowerCase();
                data = new java.util.ArrayList<>();
                for (Prediction p : full) {
                    if (p.title != null && p.title.toLowerCase().contains(lower)) {
                        data.add(p);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_prediction, parent, false);
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Prediction p = data.get(pos);
            h.itemView.setOnClickListener(v -> {
                Context c = v.getContext();
                Intent i = new Intent(c, EditPredictionActivity.class);
                i.putExtra("id", p.id);
                c.startActivity(i);
            });
            h.tvTitle.setText(p.title);

            String meta = "p=" + String.format(java.util.Locale.US, "%.1f", p.probability) + "% • " +
                    java.text.DateFormat.getDateTimeInstance().format(p.createdAt) +
                    ((p.resolved && p.outcomeYes != null) ? (" • Resolved: " + (p.outcomeYes ? "YES" : "NO")) : "");
            h.tvMeta.setText(meta);

            // Tag
            if (p.tagLabel != null && !p.tagLabel.isEmpty() && p.tagColor != 0) {
                h.tvTagLabel.setText(p.tagLabel);
                h.tvTagLabel.setVisibility(View.VISIBLE);
                h.viewTag.setVisibility(View.VISIBLE);
                h.viewTag.setBackgroundColor(p.tagColor);
            } else {
                h.tvTagLabel.setVisibility(View.GONE);
                h.viewTag.setVisibility(View.GONE);
            }

            boolean canResolve = !p.resolved;
            h.btnYes.setEnabled(canResolve);
            h.btnNo.setEnabled(canResolve);
            h.btnYes.setOnClickListener(v -> { if (canResolve) vm.resolve(p.id, true); });
            h.btnNo.setOnClickListener(v -> { if (canResolve) vm.resolve(p.id, false); });
        }
        @Override public int getItemCount() { return data == null ? 0 : data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvMeta, tvTagLabel;
            View btnYes, btnNo, viewTag;

            VH(@NonNull View itemView) {
                super(itemView);
                tvTitle    = itemView.findViewById(R.id.tvTitle);
                tvMeta     = itemView.findViewById(R.id.tvMeta);
                tvTagLabel = itemView.findViewById(R.id.tvTagLabel);
                viewTag    = itemView.findViewById(R.id.viewTag);
                btnYes     = itemView.findViewById(R.id.btnYes);
                btnNo      = itemView.findViewById(R.id.btnNo);
            }
        }
    }
}