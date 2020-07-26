package com.litus_animae.refitted.data.room;

import android.os.AsyncTask;
import android.util.Log;


public class CloseDatabaseTask extends AsyncTask<ContextWeakReference, Void, Void> {
    private static final String TAG = "RoomDataService.CloseDatabaseTask";

    @Override
    protected Void doInBackground(ContextWeakReference... contexts) {
        if (contexts.length > 0) {
            ContextWeakReference context = contexts[0];
            Log.d(TAG, "doInBackground: context " + context.get().toString() + " waiting to create Room database, " + Thread.currentThread().getName());
            synchronized (ExerciseRoom.class) {
                Log.d(TAG, "doInBackground: context " + context.get().toString() + " has exclusive hashset access, " + Thread.currentThread().getName());
                if (RoomExerciseDataService.room != null && RoomExerciseDataService.room.getValue() != null) {
                    RoomExerciseDataService.room.getValue().close();
                    RoomExerciseDataService.room.postValue(null);
                } else if (RoomExerciseDataService.room.getValue() == null){
                    Log.w(TAG, "doInBackground: context " + context.get().toString() + " found empty hashset entry, " + Thread.currentThread().getName());
                }
            }
        } else {
            Log.e(TAG, "doInBackground: insufficient arguments given");
        }
        return null;
    }
}
