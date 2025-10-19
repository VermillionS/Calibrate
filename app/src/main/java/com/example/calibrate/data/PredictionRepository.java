package com.example.calibrate.data;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PredictionRepository {
    private final PredictionDao dao;
    private final Executor exec = Executors.newSingleThreadExecutor();

    public PredictionRepository(Context ctx) { this.dao = Di.db(ctx).predictionDao(); }

    public LiveData<List<Prediction>> all() { return dao.observeAll(); }

    public void add(String title, double prob, @Nullable String tagLabel, int tagColor) {
        exec.execute(() -> {
            Prediction p = new Prediction();
            p.title = title;
            p.probability = prob;
            p.createdAt = System.currentTimeMillis();
            p.resolved = false;
            p.outcomeYes = null;
            p.description = null;
            p.tagLabel = tagLabel;
            p.tagColor = tagColor;
            dao.insert(p);
        });
    }

    public void add(String title, double prob, @Nullable String desc, @Nullable String tagLabel, int tagColor) {
        exec.execute(() -> {
            long now = System.currentTimeMillis();
            Prediction p = new Prediction(title, prob, now, desc, tagLabel, tagColor);
            dao.insert(p);
        });
    }

    public Prediction getByIdSync(long id) { return dao.getById(id); }

    public void updateCore(long id, String title, double prob,
                           @Nullable String desc, @Nullable String tagLabel, int tagColor) {
        exec.execute(() -> dao.updateCore(id, title, prob, desc, tagLabel, tagColor));
    }

    public void resolve(long id, boolean outcomeYes) {
        exec.execute(() -> dao.updateResolution(id, true, outcomeYes));
    }

    public void unresolve(long id) { exec.execute(() -> dao.updateResolution(id, false, null)); }

    public void delete(long id) { exec.execute(() -> dao.deleteById(id)); }

    public void bulkUpdateTag(String oldLabel, String newLabel, int newColor) {
        exec.execute(() -> dao.bulkUpdateTag(oldLabel, newLabel, newColor));
    }

    public void renameTagEverywhere(String oldLabel, String newLabel, int newColor) {
        exec.execute(() -> dao.renameTagEverywhere(oldLabel, newLabel, newColor));
    }

    public void clearTagEverywhere(String label) {
        exec.execute(() -> dao.clearTagEverywhere(label));
    }

    public List<Prediction> getAllSync() { return dao.getAllSync(); }

    public void deleteAll() { exec.execute(dao::deleteAll); }

    public void replaceAll(List<Prediction> list) {
        exec.execute(() -> {
            dao.deleteAll();
            for (Prediction p : list) {
                dao.insert(p);
            }
        });
    }
}
