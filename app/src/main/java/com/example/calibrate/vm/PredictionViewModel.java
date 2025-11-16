package com.example.calibrate.vm;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.calibrate.data.Prediction;
import com.example.calibrate.data.PredictionRepository;
import java.util.List;

public class PredictionViewModel extends AndroidViewModel {
    private final PredictionRepository repo;
    private final LiveData<List<Prediction>> predictions;

    public PredictionViewModel(@NonNull Application app) {
        super(app);
        repo = new PredictionRepository(app);
        predictions = repo.all();
    }

    public LiveData<List<Prediction>> predictions() { return predictions; }

    public Prediction getOnce(long id) { return repo.getByIdSync(id); }
    public void updateCore(long id, String t, double p, @Nullable String d, @Nullable String tag, int color) {
        repo.updateCore(id, t, p, d, tag, color);
    }
    public void resolve(long id, boolean yes) { repo.resolve(id, yes); }
    public void unresolve(long id) { repo.unresolve(id); }
    public void delete(long id) { repo.delete(id); }
    public void bulkUpdateTag(String oldL, String newL, int newC) { repo.bulkUpdateTag(oldL, newL, newC); }

    public void renameTagEverywhere(String oldLabel, String newLabel, int newColor) {
        repo.renameTagEverywhere(oldLabel, newLabel, newColor);
    }

    public void clearTagEverywhere(String label) {
        repo.clearTagEverywhere(label);
    }

    public void addPrediction(String title, double prob, @Nullable String desc, @Nullable String tagLabel, int tagColor) {
        repo.add(title, prob, desc, tagLabel, tagColor);
    }

    public List<Prediction> exportAllSync() { return repo.getAllSync(); }
    public void importReplaceAll(List<Prediction> list) { repo.replaceAll(list); }
    public void deleteAll() { repo.deleteAll(); }
}
