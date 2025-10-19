package com.example.calibrate.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = { Prediction.class }, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract PredictionDao predictionDao();
}
