package com.example.calibrate.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "predictions")
public class Prediction {
    @PrimaryKey(autoGenerate = true) public long id;

    @NonNull public String title = "";
    public double probability;
    public long createdAt;

    public boolean resolved;
    @Nullable public Boolean outcomeYes;

    @Nullable public String description;

    @Nullable public String tagLabel;
    public int tagColor;

    public Prediction(String title, double probability, long createdAt, @Nullable String description, @Nullable String tagLabel, int tagColor) {
        this.title = title;
        this.probability = probability;
        this.createdAt = createdAt;
        this.description = description;
        this.tagLabel = tagLabel;
        this.tagColor = tagColor;
        this.resolved = false;
        this.outcomeYes = null;
    }

    public Prediction() { }
}
