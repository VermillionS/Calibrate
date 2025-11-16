package com.example.calibrate.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.example.calibrate.R;
import com.example.calibrate.data.Prediction;
import com.example.calibrate.vm.PredictionViewModel;
import com.example.calibrate.data.TagStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EditPredictionActivity extends BaseActivity {

    private PredictionViewModel vm;
    private long pid;
    private EditText etTitle, etProb, etDesc;
    private Spinner spTag;
    private View vSwatch;
    private RadioGroup rgResolution;
    private ArrayAdapter<TagOpt> tagAdapter;
    private ArrayList<TagOpt> tagOptions;
    private String originalTagLabel;
    private int originalTagColor;
    private int lastRealIndex = 0;
    @Nullable private TagOpt lastRealTag = null;

    private static class TagOpt {
        final String label; final int color; TagOpt(String l, int c){ label=l;color=c; }
        @Override public String toString(){ return label; }
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_prediction);

        pid = getIntent().getLongExtra("id", -1L);

        if (pid < 0) { finish(); return; }

        vm = new ViewModelProvider(this).get(PredictionViewModel.class);

        etTitle = findViewById(R.id.etTitle);
        etProb  = findViewById(R.id.etProb);
        etDesc  = findViewById(R.id.etDesc);
        spTag   = findViewById(R.id.spinnerTag);
        vSwatch = findViewById(R.id.viewTagColor);
        rgResolution = findViewById(R.id.rgResolution);

        refreshTagSpinner(null, 0);

        new Thread(() -> {
            Prediction p = vm.getOnce(pid);

            if (p == null) {
                runOnUiThread(this::finish);
                return;
            }

            originalTagLabel = p.tagLabel;
            originalTagColor = p.tagColor;

            runOnUiThread(() -> bindPrediction(p));
        }).start();


        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btnCancel).setOnClickListener(v -> {
            revertTagChanges();
            finish();
        });

        findViewById(R.id.btnEditTag).setOnClickListener(v -> {
            Object sel = spTag.getSelectedItem();
            if (sel instanceof TagOpt) {
                TagOpt to = (TagOpt) sel;
                if (to.color == -1 || "No tag".equals(to.label)) {

                    android.widget.Toast.makeText(this, "Pick a tag first", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    showEditTagDialog(new TagStore.Tag(to.label, to.color));
                }
            }
        });
    }

    private void refreshTagSpinner(@Nullable String preselectLabel, int preselectColor) {
        List<TagStore.Tag> saved = TagStore.getAll(this);
        tagOptions = new ArrayList<>();
        tagOptions.add(new TagOpt("No tag", 0));
        for (TagStore.Tag t : saved) tagOptions.add(new TagOpt(t.label, t.color));
        tagOptions.add(new TagOpt("New tag…", -1));

        if (tagAdapter == null) {
            tagAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, tagOptions);
            tagAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
            spTag.setAdapter(tagAdapter);

            spTag.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    TagOpt t = tagOptions.get(pos);

                    if (t.color == -1) {
                        vSwatch.setBackgroundColor(0x00000000);
                        showCreateTagDialogWithRevert();
                    } else {
                        vSwatch.setBackgroundColor(t.color == 0 ? 0x00000000 : t.color);
                        if (t.color != 0) {
                            lastRealIndex = pos;
                            lastRealTag   = t;
                        }
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        } else {
            tagAdapter.clear();
            tagAdapter.addAll(tagOptions);
            tagAdapter.notifyDataSetChanged();
        }

        int idx = 0;
        if (preselectLabel != null) {
            for (int i = 0; i < tagOptions.size(); i++) {
                if (tagOptions.get(i).label.equalsIgnoreCase(preselectLabel)) { idx = i; break; }
            }
        }
        spTag.setSelection(idx);
        vSwatch.setBackgroundColor((idx == 0 ? 0x00000000 : (preselectColor == 0 ? tagOptions.get(idx).color : preselectColor)));

        TagOpt sel = tagOptions.get(idx);
        if (sel.color != 0 && sel.color != -1) {
            lastRealIndex = idx;
            lastRealTag   = sel;
        } else {
            lastRealIndex = 0;
            lastRealTag   = null;
        }
    }

    private void revertTagChanges() {
        if (originalTagLabel != null) {
            TagStore.addOrUpdate(this, new TagStore.Tag(originalTagLabel, originalTagColor));
            vm.bulkUpdateTag(originalTagLabel, originalTagLabel, originalTagColor);
        }
    }

    private void showCreateTagDialogWithRevert() {
        final EditText etName = new EditText(this);
        etName.setHint("Tag name");
        final EditText etHex  = new EditText(this);
        etHex.setHint("Hex code, eg #AARRGGBB");

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(24,16,24,0);
        ll.addView(etName);
        ll.addView(etHex);

        final int[] pickedColor = { PALETTE[0] };

        View grid = buildColorGrid(ll, PALETTE, pickedColor[0], c -> {
            pickedColor[0] = c;
            etHex.setText(String.format(java.util.Locale.US, "#%08X", c));
        });
        ll.addView(grid);

        etHex.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) pickedColor[0] = parseColorOr(etHex.getText().toString(), pickedColor[0]);
        });

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Calibrate_Dialog)
                .setTitle("New Tag")
                .setView(ll)
                .setPositiveButton("Save", (d,w)->{
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) name = "Tag";
                    int color = parseColorOr(etHex.getText().toString(), pickedColor[0]);

                    TagStore.addOrUpdate(this, new TagStore.Tag(name, color));
                    vm.renameTagEverywhere(name, name, color);

                    refreshTagSpinner(name, color);

                    for (int i = 0; i < tagOptions.size(); i++) {
                        TagOpt t = tagOptions.get(i);
                        if (t.color != -1 && t.label.equalsIgnoreCase(name)) {
                            lastRealIndex = i;
                            lastRealTag   = t;
                            break;
                        }
                    }
                })
                .setNegativeButton("Cancel", (d,w)->{
                    int idx = (lastRealIndex < 0) ? 0 : lastRealIndex;
                    spTag.setSelection(idx);
                    if (lastRealTag == null || lastRealTag.color == 0) {
                        vSwatch.setBackgroundColor(0x00000000);
                    } else {
                        vSwatch.setBackgroundColor(lastRealTag.color);
                    }
                })
                .show();
    }

    private void bindPrediction(Prediction p) {
        if (p == null) { finish(); return; }
        etTitle.setText(p.title);
        String probStr = String.format(Locale.US, "%.2f", p.probability)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
        etProb.setText(probStr);
        etDesc.setText(p.description == null ? "" : p.description);

        if (!p.resolved) rgResolution.check(R.id.rbUnresolved);
        else if (Boolean.TRUE.equals(p.outcomeYes)) rgResolution.check(R.id.rbYes);
        else rgResolution.check(R.id.rbNo);

        List<TagStore.Tag> saved = TagStore.getAll(this);
        tagOptions = new ArrayList<>();
        tagOptions.add(new TagOpt("No tag", 0));
        int preselect = 0;
        for (int i = 0; i < saved.size(); i++) {
            TagStore.Tag t = saved.get(i);
            tagOptions.add(new TagOpt(t.label, t.color));
            if (p.tagLabel != null && p.tagLabel.equalsIgnoreCase(t.label)) preselect = i + 1;
        }
        tagOptions.add(new TagOpt("New tag…", -1));

        tagAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, tagOptions);
        tagAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spTag.setAdapter(tagAdapter);
        spTag.setSelection(preselect);
        vSwatch.setBackgroundColor(preselect == 0 ? 0x00000000 : tagOptions.get(preselect).color);

        TagOpt sel = tagOptions.get(preselect);
        if (sel.color != 0 && sel.color != -1) {
            lastRealIndex = preselect;
            lastRealTag   = sel;
        } else {
            lastRealIndex = 0;
            lastRealTag   = null;
        }

        spTag.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                TagOpt t = tagOptions.get(pos);
                if (t.color == -1) {
                    vSwatch.setBackgroundColor(0x00000000);
                    promptNewTagWithRevert();
                } else {
                    vSwatch.setBackgroundColor(t.color == 0 ? 0x00000000 : t.color);
                    if (t.color != 0) {
                        lastRealIndex = pos;
                        lastRealTag   = t;
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void promptNewTagWithRevert() {
        final EditText etName = new EditText(this);
        etName.setHint("Tag name");

        final EditText etHex  = new EditText(this);
        etHex.setHint("Hex code, eg #AARRGGBB");

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(24, 16, 24, 0);
        ll.addView(etName);

        final int[] pickedColor = { PALETTE[0] };

        View grid = buildColorGrid(ll, PALETTE, pickedColor[0], c -> {
            pickedColor[0] = c;
            etHex.setText(String.format(java.util.Locale.US, "#%08X", c));
        });
        ll.addView(grid);
        ll.addView(etHex);

        etHex.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                int c2 = parseColorOr(etHex.getText().toString(), pickedColor[0]);
                pickedColor[0] = c2;
            }
        });

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Calibrate_Dialog)
                .setTitle("New tag")
                .setView(ll)
                .setPositiveButton("OK", (d,w)->{

                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        int color = parseColorOr(etHex.getText().toString(), pickedColor[0]);

                        TagStore.addOrUpdate(this, new TagStore.Tag(name, color));
                        vm.renameTagEverywhere(name, name, color);

                        refreshTagSpinner(name, color);

                        for (int i = 0; i < tagOptions.size(); i++) {
                            TagOpt t = tagOptions.get(i);
                            if (t.color != -1 && t.label.equalsIgnoreCase(name)) {
                                lastRealIndex = i;
                                lastRealTag   = t;
                                break;
                            }
                        }
                    } else {
                        toast("Must enter a new Tag's Name");
                        int idx = (lastRealIndex < 0) ? 0 : lastRealIndex;
                        spTag.setSelection(idx);
                    }
                })
                .setNegativeButton("Cancel", (d,w)->{
                    int idx = (lastRealIndex < 0) ? 0 : lastRealIndex;
                    spTag.setSelection(idx);
                    if (lastRealTag == null || lastRealTag.color == 0) {
                        vSwatch.setBackgroundColor(0x00000000);
                    } else {
                        vSwatch.setBackgroundColor(lastRealTag.color);
                    }
                })
                .show();
    }

    private void save() {
        String title = sanitizeTitle(String.valueOf(etTitle.getText()));
        if (title.isEmpty()) { toast("Enter a title, or cancel"); return; }

        Double prob = parseProb(String.valueOf(etProb.getText()));
        if (prob == null) { toast("Enter a probability, or cancel"); return; }

        if (prob <= 0) { toast("Probability must be greater than 0%"); return; }
        if (prob >= 100) { toast("Probability must be less than 100%"); return; }
        double p = clampPct(prob);

        String desc = String.valueOf(etDesc.getText()).trim();
        if (desc.isEmpty()) desc = null;

        TagOpt sel = (TagOpt) spTag.getSelectedItem();

        int checked = rgResolution.getCheckedRadioButtonId();
        if (checked == R.id.rbUnresolved) {
            vm.unresolve(pid);
        } else if (checked == R.id.rbYes) {
            vm.resolve(pid, true);
        } else if (checked == R.id.rbNo) {
            vm.resolve(pid, false);
        }

        String tagLabel = (sel == null || sel.color == 0 || "No tag".equalsIgnoreCase(sel.label)) ? null : sel.label;
        int tagColor = 0;

        if (tagLabel != null && !tagLabel.trim().isEmpty()) {
            TagStore.Tag t = TagStore.get(this, tagLabel);
            tagColor = (t != null) ? t.color : 0;
        }

        vm.updateCore(pid, title, p, desc, tagLabel, tagColor);

        finish();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete prediction?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (d,w) -> { vm.delete(pid); finish(); })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String sanitizeTitle(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\p{Cntrl}", "");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > 200) s = s.substring(0, 200);
        return s;
    }

    private static Double parseProb(String s) {
        try { String t = s==null?"":s.trim(); if (t.isEmpty()) return null; return Double.parseDouble(t); }
        catch (Exception e) { return null; }
    }

    private static double clampPct(double v){ return v<0?0:(v>100?100:v); }

    private static int parseColorOr(String hex, int fallback) {
        try { String t = hex == null ? "" : hex.trim(); if (!t.startsWith("#")) t = "#" + t; return android.graphics.Color.parseColor(t); }
        catch (Exception e) { return fallback; }
    }

    private void toast(String s){ Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static final int[] PALETTE = new int[]{
            0xFFFF0000,
            0xFFFF3A00,
            0xFFFF7F00,
            0xFFFFB300,
            0xFFC8FF00,
            0xFF80FF00,
            0xFF40FF00,
            0xFF00FF40,
            0xFF00FF80,
            0xFF00FFBF,
            0xFF00FFFF,
            0xFF00BFFF,
            0xFF0080FF,
            0xFF0040FF,
            0xFF4000FF,
            0xFF8000FF,
            0xFFBF00FF,
            0xFFFF00FF,
            0xFFFF00BF,
            0xFFFF69B4
    };

    private static int dp(View anchor, int dps) {
        float den = anchor.getResources().getDisplayMetrics().density;
        return Math.round(den * dps);
    }

    private View buildColorGrid(View anchorForDp, int[] palette, int initiallySelectedColor,
                                java.util.function.IntConsumer onPick) {
        android.widget.GridLayout grid = new android.widget.GridLayout(anchorForDp.getContext());
        grid.setColumnCount(5);
        grid.setRowCount((int) Math.ceil(palette.length / 4.0));
        grid.setPadding(0, dp(anchorForDp, 8), 0, 0);

        final View[] selected = new View[1];

        for (int color : palette) {
            View swatch = new View(anchorForDp.getContext());
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            int size = dp(anchorForDp, 36);
            lp.width = size; lp.height = size;
            lp.setMargins(dp(anchorForDp, 6), dp(anchorForDp, 6), dp(anchorForDp, 6), dp(anchorForDp, 6));
            swatch.setLayoutParams(lp);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(anchorForDp, 18));
            bg.setColor(color);

            bg.setStroke(dp(anchorForDp, 2), (color == initiallySelectedColor) ? 0xFFFFFFFF : 0x00000000);
            swatch.setBackground(bg);
            if (color == initiallySelectedColor) selected[0] = swatch;

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

    private void showEditTagDialog(@NonNull TagStore.Tag existing) {
        final EditText etName = new EditText(this);
        etName.setHint("Tag name");
        etName.setText(existing.label);

        final EditText etHex  = new EditText(this);
        etHex.setHint("Hex code, eg #AARRGGBB");
        etHex.setText(String.format(java.util.Locale.US, "#%08X", existing.color));

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(24, 16, 24, 0);
        ll.addView(etName);

        final int[] pickedColor = { existing.color };

        View grid = buildColorGrid(ll, PALETTE, existing.color, c -> {
            pickedColor[0] = c;
            etHex.setText(String.format(java.util.Locale.US, "#%08X", c));
        });
        ll.addView(grid);
        ll.addView(etHex);

        etHex.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) pickedColor[0] = parseColorOr(etHex.getText().toString(), pickedColor[0]);
        });

        androidx.appcompat.app.AlertDialog dlg =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Calibrate_Dialog)
                        .setTitle("Edit tag")
                        .setView(ll)
                        .setPositiveButton("Save", null)
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton("Delete", null)
                        .create();

        dlg.setOnShowListener(d -> {
            Button btnSave   = dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            Button btnDelete = dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL);

            btnSave.setOnClickListener(v -> {
                String newLabelRaw = etName.getText() == null ? "" : etName.getText().toString();
                String newLabel    = newLabelRaw.trim();
                int    newColor    = parseColorOr(etHex.getText() == null ? "" : etHex.getText().toString(), pickedColor[0]);

                if (isReservedOrInvalidTagName(newLabel)) {
                    toast("Invalid tag name.");
                    return;
                }

                final String oldLabel = existing.label;
                final int    oldColor = existing.color;

                boolean nameChanged  = !oldLabel.equalsIgnoreCase(newLabel);
                boolean colorChanged = (oldColor != newColor);

                if (!nameChanged && !colorChanged) {
                    dlg.dismiss();
                    return;
                }

                if (nameChanged) {
                    TagStore.remove(this, oldLabel);
                    TagStore.addOrUpdate(this, new TagStore.Tag(newLabel, newColor));

                    vm.renameTagEverywhere(oldLabel, newLabel, newColor);

                    originalTagLabel = newLabel;
                    originalTagColor = newColor;

                    refreshTagSpinner(newLabel, newColor);
                    selectTagInSpinnerByLabel(newLabel, newColor);
                } else {
                    TagStore.addOrUpdate(this, new TagStore.Tag(oldLabel, newColor));
                    vm.renameTagEverywhere(oldLabel, oldLabel, newColor);

                    originalTagColor = newColor;

                    refreshTagSpinner(oldLabel, newColor);
                    selectTagInSpinnerByLabel(oldLabel, newColor);
                }

                vSwatch.setBackgroundColor(newColor);
                dlg.dismiss();
            });

            btnDelete.setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Calibrate_Dialog)
                    .setTitle("Delete tag?")
                    .setMessage("Remove this tag from Tag Library and clear it from existing predictions?")
                    .setPositiveButton("Delete", (d2,w2)->{
                        vm.clearTagEverywhere(existing.label);
                        TagStore.remove(this, existing.label);
                        refreshTagSpinner(null, 0);
                        android.widget.Toast.makeText(this, "Tag deleted", android.widget.Toast.LENGTH_SHORT).show();
                        dlg.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
        });

        dlg.show();
    }

    private void selectTagInSpinnerByLabel(@NonNull String label, int fallbackColor) {
        int select = 0;
        for (int i = 0; i < tagOptions.size(); i++) {
            TagOpt t = tagOptions.get(i);
            if (t.color != -1 && t.label.equalsIgnoreCase(label)) { select = i; break; }
        }
        spTag.setSelection(select);
        int color = (select == 0) ? 0 : (tagOptions.get(select).color == 0 ? fallbackColor : tagOptions.get(select).color);
        vSwatch.setBackgroundColor(color);

        TagOpt sel = tagOptions.get(select);
        if (sel.color != 0 && sel.color != -1) {
            lastRealIndex = select;
            lastRealTag   = sel;
        } else {
            lastRealIndex = 0;
            lastRealTag   = null;
        }
    }

    private boolean isReservedOrInvalidTagName(@Nullable String name) {
        if (name == null) return true;
        String n = name.trim();
        if (n.isEmpty()) return true;
        if (n.equalsIgnoreCase("No tag")) return true;
        if (n.toLowerCase(Locale.US).startsWith("new tag")) return true;
        if (n.contains("||||")) return true;
        return false;
    }
}
