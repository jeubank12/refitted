package com.litus_animae.refitted.threads;

import android.content.Context;
import android.util.Log;

import com.litus_animae.refitted.data.room.ExerciseRoom;
import com.litus_animae.refitted.data.room.asynctask.RoomExerciseDataService;
import com.litus_animae.refitted.models.SetRecord;

public class StoreRecordsRunnable implements Runnable {
    private static final String TAG = "StoreRecordsRunnable";
    private final Context applicationContext;
    private final SetRecord record;

    public StoreRecordsRunnable(Context context, SetRecord record){
        this.applicationContext = context;
        this.record = record;
    }

    @Override
    public void run() {
        try {
            ExerciseRoom roomDb = RoomExerciseDataService.getExerciseRoom(applicationContext);
            roomDb.getExerciseDao().storeExerciseRecord(record);
        } catch (Exception ex){
            Log.e(TAG, "run: exception during store", ex);
        }
    }
}
