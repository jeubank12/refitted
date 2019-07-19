package com.litus_animae.refitted.threads;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import com.litus_animae.refitted.Constants;
import com.litus_animae.refitted.data.DynamoDataService;
import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseSet;

public class GetExerciseRunnable implements Runnable {

    private static final String TAG = "GetExerciseRunnable";
    private WeakReference<Handler> mainThreadHandler;
    private Context applicationContext;
    private String day;
    private String workoutId;

    public GetExerciseRunnable(Context context, Handler mainThreadHandler,
                               String day, String workoutId) {
        this.applicationContext = context.getApplicationContext();
        this.mainThreadHandler = new WeakReference<>(mainThreadHandler);
        this.day = day;
        this.workoutId = workoutId;
    }

    @Override
    public void run() {
        DynamoDataService db = new DynamoDataService(applicationContext);
        ArrayList<ExerciseSet> exerciseSets = db.GetExerciseSets(day, workoutId);
        Message msg;
        if (!exerciseSets.isEmpty()) {
            db.GetExercises(workoutId, exerciseSets);
            exerciseSets.sort((o1, o2) -> o1.getStep().compareTo(o2.getStep()));

            ArrayList<ExerciseRecord> records = new ArrayList<>(exerciseSets.size());
            for (ExerciseSet e : exerciseSets){
                records.add(new ExerciseRecord(e));
            }

            Log.d(TAG, "run: retrieval success");
            msg = Message.obtain(null, Constants.EXERCISE_LOAD_SUCCESS);
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList("exercise_load", exerciseSets);
            bundle.putParcelableArrayList("exercise_records", records);
            msg.setData(bundle);
        } else {
            Log.d(TAG, "run: retrieval failure");
            msg = Message.obtain(null, Constants.EXERCISE_LOAD_FAIL);
        }
        mainThreadHandler.get().sendMessage(msg);
    }

}
