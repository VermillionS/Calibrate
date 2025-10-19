package com.example.calibrate.data;

import android.content.Context;
import androidx.room.Room;

public class Di {
    private static volatile AppDatabase db;

    public static AppDatabase db(Context ctx) {
        if (db == null) {
            synchronized (Di.class) {
                if (db == null) {
                    db = Room.databaseBuilder(ctx.getApplicationContext(),
                                    AppDatabase.class, "calibrator.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return db;
    }
}
