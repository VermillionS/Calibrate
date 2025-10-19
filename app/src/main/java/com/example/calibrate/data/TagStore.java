package com.example.calibrate.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class TagStore {
    private static final String PREFS = "calibrator_tags";
    private static final String KEY = "tags_json";

    public static final class Tag {
        public final String label;
        public final int color;

        public Tag(String label, int color) {
            this.label = label;
            this.color = color;
        }

        @Override public String toString() { return label; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Tag)) return false;
            Tag t = (Tag) o;
            return color == t.color && safeEq(label, t.label, true);
        }

        @Override
        public int hashCode() {
            return (label == null ? 0 : label.toLowerCase().hashCode()) * 31 + color;
        }
    }

    private TagStore() {}

    public static List<Tag> getAll(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String raw = sp.getString(KEY, "[]");
        List<Tag> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String label = o.optString("label", "").trim();
                int color = o.optInt("color", 0);
                if (!label.isEmpty()) {
                    out.add(new Tag(label, color));
                }
            }
        } catch (Exception ignored) {}

        sortByLabel(out);
        return out;
    }

    public static void saveAll(Context ctx, List<Tag> tags) {
        JSONArray arr = new JSONArray();
        for (Tag t : tags) {
            JSONObject o = new JSONObject();
            try {
                o.put("label", t.label);
                o.put("color", t.color);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }

    public static void addOrUpdate(Context ctx, Tag tag) {
        if (tag == null || tag.label == null || tag.label.trim().isEmpty()) return;

        List<Tag> all = new ArrayList<>(getAll(ctx));
        boolean updated = false;
        for (int i = 0; i < all.size(); i++) {
            Tag t = all.get(i);
            if (safeEq(t.label, tag.label, true)) {
                all.set(i, new Tag(tag.label.trim(), tag.color));
                updated = true;
                break;
            }
        }
        if (!updated) {
            all.add(new Tag(tag.label.trim(), tag.color));
        }

        sortByLabel(all);
        writeAll(ctx, all);
    }

    public static void remove(Context ctx, String label) {
        if (label == null || label.trim().isEmpty()) return;

        List<Tag> all = new ArrayList<>(getAll(ctx));
        boolean changed = false;
        for (Iterator<Tag> it = all.iterator(); it.hasNext(); ) {
            Tag t = it.next();
            if (safeEq(t.label, label, true)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            writeAll(ctx, all);
        }
    }

    public static @Nullable Tag get(Context ctx, String label) {
        if (label == null) return null;
        for (Tag t : getAll(ctx)) {
            if (safeEq(t.label, label, true)) return t;
        }
        return null;
    }

    public static void setAll(Context ctx, List<Tag> tags) {
        if (tags == null) tags = Collections.emptyList();
        List<Tag> copy = new ArrayList<>(tags.size());
        for (Tag t : tags) {
            if (t == null || t.label == null) continue;
            String lab = t.label.trim();
            if (lab.isEmpty()) continue;
            copy.add(new Tag(lab, t.color));
        }
        sortByLabel(copy);
        writeAll(ctx, copy);
    }

    private static void writeAll(Context ctx, List<Tag> tags) {
        JSONArray arr = new JSONArray();
        try {
            for (Tag t : tags) {
                JSONObject o = new JSONObject();
                o.put("label", t.label);
                o.put("color", t.color);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        sp.edit().putString(KEY, arr.toString()).apply();
    }

    private static void sortByLabel(List<Tag> list) {
        Collections.sort(list, new Comparator<Tag>() {
            @Override public int compare(Tag a, Tag b) {
                String la = a.label == null ? "" : a.label.toLowerCase();
                String lb = b.label == null ? "" : b.label.toLowerCase();
                return la.compareTo(lb);
            }
        });
    }

    private static boolean safeEq(@Nullable String a, @Nullable String b, boolean ignoreCase) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return ignoreCase ? a.equalsIgnoreCase(b) : a.equals(b);
    }

}
