package com.litus_animae.refitted.data.room.asynctask;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;


import com.litus_animae.refitted.data.room.ContextWeakReference;

import java.lang.ref.WeakReference;

public class RoomExerciseDataService {

    private static final String TAG = "RoomDataService";
    public static final String db_name = "dev01.2.db";
    public static final MutableLiveData<ExerciseRoom> room = new MutableLiveData<ExerciseRoom>();

    public static ExerciseRoom getExerciseRoom(Context context) {
        Log.i(TAG, "getExerciseRoom: context " + context.toString() + " requested Room database, " + Thread.currentThread().getName());
        ContextWeakReference contextRef = new ContextWeakReference(context);
        if (room.getValue() == null) {
            Log.i(TAG, "getExerciseRoom: context " + context.toString() + " waiting to create Room database, " + Thread.currentThread().getName());
            synchronized (ExerciseRoom.class) {
                Log.i(TAG, "getExerciseRoom: context " + context.toString() + " has exclusive creation access, " + Thread.currentThread().getName());
                if (room.getValue() == null) {
                    ExerciseRoom newRoom = Room.databaseBuilder(context.getApplicationContext(),
                            ExerciseRoom.class, db_name)
                            .addMigrations(ExerciseRoom.Companion.getMIGRATION_1_2(), ExerciseRoom.Companion.getMIGRATION_2_3(), ExerciseRoom.Companion.getMIGRATION_3_4())
                            .build();
                    Log.i(TAG, "getExerciseRoom: context " + context.toString() + " opened the database, " + Thread.currentThread().getName());
                    room.setValue(newRoom);
                }
            }
        }
        return room.getValue();
    }

    public static LiveData<ExerciseRoom> getExerciseRoomAsync(WeakReference<Context> context) {
        Log.i(TAG, "getExerciseRoomAsync: context " + context.get().toString() + " requested Room database, " + Thread.currentThread().getName());
        ContextWeakReference contextRef = new ContextWeakReference(context);
        if (room.getValue() == null) {
            Log.d(TAG, "getExerciseRoomAsync: context " + context.get().toString() + " submitting request to create db, " + Thread.currentThread().getName());
            new GetDatabaseTask(room).execute(contextRef);
        }
        return room;
    }

    public static void closeExerciseRoom(Context context) {
        synchronized (ExerciseRoom.class) {
            ContextWeakReference contextRef = new ContextWeakReference(context);
            if (room != null && room.getValue() != null) {
                room.getValue().close();
                room.setValue(null);
            }
        }
    }

    static void closeExerciseRoomAsync(Context context) {
        Log.i(TAG, "closeExerciseRoomAsync: context " + context.toString() + " requested to close the database, " + Thread.currentThread().getName());
        new CloseDatabaseTask().execute(new ContextWeakReference(context));
    }

}
