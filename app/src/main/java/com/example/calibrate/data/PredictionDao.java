package com.example.calibrate.data;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface PredictionDao {

    @Query("SELECT * FROM predictions ORDER BY createdAt DESC")
    LiveData<List<Prediction>> observeAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Prediction p);

    @Query("SELECT * FROM predictions WHERE id=:id LIMIT 1")
    Prediction getById(long id);

    @Query("UPDATE predictions SET " +
            "title=:title, probability=:prob, description=:desc, " +
            "tagLabel=:tagLabel, tagColor=:tagColor WHERE id=:id")
    void updateCore(long id, String title, double prob,
                    @Nullable String desc, @Nullable String tagLabel, int tagColor);

    @Query("UPDATE predictions SET resolved=:resolved, outcomeYes=:outcomeYes WHERE id=:id")
    void updateResolution(long id, boolean resolved, @Nullable Boolean outcomeYes);

    @Query("DELETE FROM predictions WHERE id=:id")
    void deleteById(long id);

    @Query("UPDATE predictions SET tagLabel=:newLabel, tagColor=:newColor WHERE tagLabel=:oldLabel")
    void bulkUpdateTag(String oldLabel, String newLabel, int newColor);

    @Query("SELECT * FROM predictions")
    List<Prediction> getAllSync();

    @Update
    void update(Prediction p);

    @Query("UPDATE predictions SET tagLabel = :newLabel, tagColor = :newColor WHERE tagLabel = :oldLabel")
    int renameTagEverywhere(String oldLabel, String newLabel, int newColor);

    @Query("UPDATE predictions SET tagLabel = NULL, tagColor = 0 WHERE tagLabel = :label")
    int clearTagEverywhere(String label);

    @Query("DELETE FROM predictions")
    void deleteAll();
}