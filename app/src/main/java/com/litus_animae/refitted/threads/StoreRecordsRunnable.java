package com.litus_animae.refitted.threads;

import android.content.Context;
import android.util.Log;

import com.litus_animae.refitted.data.ExerciseRoom;
import com.litus_animae.refitted.data.RoomDataService;
import com.litus_animae.refitted.models.SetRecord;

public class StoreRecordsRunnable implements Runnable {
    private static final String TAG = "StoreRecordsRunnable";
    private final Context applicationContext;
    private final SetRecord record;
    private ExerciseRoom roomDb;

    public StoreRecordsRunnable(Context context, SetRecord record){
        this.applicationContext = context;
        this.record = record;
    }

    @Override
    public void run() {
        try {
            roomDb = RoomDataService.GetExerciseRoom(applicationContext);
            roomDb.getExerciseDao().storeExerciseRecord(record);
        } catch (Exception ex){
            Log.e(TAG, "run: exception during store", ex);
        }
    }
}
