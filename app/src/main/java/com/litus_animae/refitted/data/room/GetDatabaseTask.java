package com.litus_animae.refitted.data.room;

import android.os.AsyncTask;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;

import java.time.Instant;

public class GetDatabaseTask extends AsyncTask<ContextWeakReference, Void, Void> {
    private static final String TAG = "RoomDataService.GetDatabaseTask";

    private MutableLiveData<ExerciseRoom> result;

    public GetDatabaseTask(MutableLiveData<ExerciseRoom> result) {
        this.result = result;
    }

    @Override
    protected Void doInBackground(ContextWeakReference... contexts) {
        if (contexts.length > 0) {
            ContextWeakReference context = contexts[0];
            Instant start = Instant.now();
            Log.d(TAG, "doInBackground: context " + context.get().toString() + " waiting to create Room database, " + Thread.currentThread().getName());
            synchronized (ExerciseRoom.class) {
                Log.d(TAG, "doInBackground: context " + context.get().toString() + " has exclusive hashset access, " + Thread.currentThread().getName());
                if (result.getValue() == null) {
                    ExerciseRoom room = Room.databaseBuilder(context.get(),
                            ExerciseRoom.class, RoomExerciseDataService.db_name)
                            .addMigrations(ExerciseRoom.MIGRATION_1_2, ExerciseRoom.MIGRATION_2_3, ExerciseRoom.MIGRATION_3_4)
                            .build();
                    Log.i(TAG, "doInBackground: context " + context.get().toString() + " opened the database, " + Thread.currentThread().getName());
                    result.postValue(room);
                    Instant end = Instant.now();
                }
            }
        } else {
            Log.e(TAG, "doInBackground: insufficient arguments given");
        }
        return null;
    }
}
