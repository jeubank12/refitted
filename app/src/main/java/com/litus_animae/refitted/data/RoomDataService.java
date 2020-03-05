package com.litus_animae.refitted.data;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;


import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RoomDataService {

    private static final String TAG = "RoomDataService";
    private static final String db_name = "dev01.2.db";
    private static final MutableLiveData<ExerciseRoom> room = new MutableLiveData<ExerciseRoom>();

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
                            .addMigrations(ExerciseRoom.MIGRATION_1_2, ExerciseRoom.MIGRATION_2_3)
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

    private static class GetDatabaseTask extends AsyncTask<ContextWeakReference, Void, Void> {
        private static final String TAG = "RoomDataService.GetDatabaseTask";

        private MutableLiveData<ExerciseRoom> result;

        private GetDatabaseTask(MutableLiveData<ExerciseRoom> result) {
            this.result = result;
        }

        @Override
        protected Void doInBackground(ContextWeakReference... contexts) {
            if (contexts.length > 0) {
                ContextWeakReference context = contexts[0];
                Instant start = java.time.Instant.now();
                Log.d(TAG, "doInBackground: context " + context.get().toString() + " waiting to create Room database, " + Thread.currentThread().getName());
                synchronized (ExerciseRoom.class) {
                    Log.d(TAG, "doInBackground: context " + context.get().toString() + " has exclusive hashset access, " + Thread.currentThread().getName());
                    if (result.getValue() == null) {
                        ExerciseRoom room = Room.databaseBuilder(context.get(),
                                ExerciseRoom.class, db_name)
                                .addMigrations(ExerciseRoom.MIGRATION_1_2, ExerciseRoom.MIGRATION_2_3)
                                .build();
                        Log.i(TAG, "doInBackground: context " + context.get().toString() + " opened the database, " + Thread.currentThread().getName());
                        result.postValue(room);
                        Instant end = java.time.Instant.now();
                    }
                }
            } else {
                Log.e(TAG, "doInBackground: insufficient arguments given");
            }
            return null;
        }
    }

    private static class CloseDatabaseTask extends AsyncTask<ContextWeakReference, Void, Void> {
        private static final String TAG = "RoomDataService.CloseDatabaseTask";

        @Override
        protected Void doInBackground(ContextWeakReference... contexts) {
            if (contexts.length > 0) {
                ContextWeakReference context = contexts[0];
                Log.d(TAG, "doInBackground: context " + context.get().toString() + " waiting to create Room database, " + Thread.currentThread().getName());
                synchronized (ExerciseRoom.class) {
                    Log.d(TAG, "doInBackground: context " + context.get().toString() + " has exclusive hashset access, " + Thread.currentThread().getName());
                    if (room != null && room.getValue() != null) {
                        room.getValue().close();
                        room.postValue(null);
                    } else if (room.getValue() == null){
                        Log.w(TAG, "doInBackground: context " + context.get().toString() + " found empty hashset entry, " + Thread.currentThread().getName());
                    }
                }
            } else {
                Log.e(TAG, "doInBackground: insufficient arguments given");
            }
            return null;
        }
    }

    private static class ContextWeakReference {

        private final WeakReference<Context> context;

        public ContextWeakReference(WeakReference<Context> context) {
            this.context = context;
        }

        public ContextWeakReference(Context context) {
            this.context = new WeakReference<>(context.getApplicationContext());
        }

        @Override
        public int hashCode() {
            return Objects.hash(context.get());
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof ContextWeakReference) {
                return ((ContextWeakReference) obj).context.get().equals(context.get());
            } else {
                return false;
            }
        }

        public Context get() {
            return context.get();
        }
    }
}
