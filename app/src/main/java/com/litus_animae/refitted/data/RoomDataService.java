package com.litus_animae.refitted.data;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RoomDataService {

    private static final String TAG = "RoomDataService";
    private static final String db_name = "dev01.2.db";
    private static final ConcurrentHashMap<ContextWeakReference, MutableLiveData<ExerciseRoom>> rooms = new ConcurrentHashMap<>();

    public static ExerciseRoom getExerciseRoom(Context context) {
        ContextWeakReference contextRef = new ContextWeakReference(context);
        MutableLiveData<ExerciseRoom> room = rooms.get(contextRef);
        if (room == null || room.getValue() == null) {
            synchronized (ExerciseRoom.class) {
                room = rooms.merge(contextRef, new MutableLiveData<>(), (oldValue, newValue) -> oldValue);
                if (room.getValue() == null) {
                    ExerciseRoom newRoom = Room.databaseBuilder(context.getApplicationContext(),
                            ExerciseRoom.class, db_name)
                            .addMigrations(ExerciseRoom.MIGRATION_1_2)
                            .build();
                    room.setValue(newRoom);
                }
            }
        }
        return room.getValue();
    }

    public static LiveData<ExerciseRoom> getExerciseRoomAsync(WeakReference<Context> context) {
        ContextWeakReference contextRef = new ContextWeakReference(context);
        MutableLiveData<ExerciseRoom> room = rooms.merge(contextRef, new MutableLiveData<>(), (oldValue, newValue) -> oldValue);
        if (room.getValue() == null) {
            new GetDatabaseTask(room).execute(contextRef);
        }
        return room;
    }

    public static void closeExerciseRoom(Context context) {
        synchronized (ExerciseRoom.class) {
            ContextWeakReference contextRef = new ContextWeakReference(context);
            MutableLiveData<ExerciseRoom> room = rooms.remove(contextRef);
            if (room != null && room.getValue() != null) {
                room.getValue().close();
            }
        }
    }

    public static void closeExerciseRoomAsync(Context context) {
    }

    private static class GetDatabaseTask extends AsyncTask<ContextWeakReference, Void, Void> {

        private MutableLiveData<ExerciseRoom> result;

        private GetDatabaseTask(MutableLiveData<ExerciseRoom> result) {
            this.result = result;
        }

        @Override
        protected Void doInBackground(ContextWeakReference... contexts) {
            if (contexts.length > 0) {
                ContextWeakReference context = contexts[0];
                synchronized (ExerciseRoom.class) {
                    if (result.getValue() == null) {
                        ExerciseRoom room = Room.databaseBuilder(context.get(),
                                ExerciseRoom.class, db_name)
                                .addMigrations(ExerciseRoom.MIGRATION_1_2)
                                .build();
                        result.postValue(room);
                    }
                }
            }
            return null;
        }
    }

    private static class CloseDatabaseTask extends AsyncTask<ContextWeakReference, Void, Void> {

        private MutableLiveData<ExerciseRoom> result;

        private CloseDatabaseTask(MutableLiveData<ExerciseRoom> result) {
            this.result = result;
        }

        @Override
        protected Void doInBackground(ContextWeakReference... contexts) {
            if (contexts.length > 0) {
                ContextWeakReference context = contexts[0];
                synchronized (ExerciseRoom.class) {
                    MutableLiveData<ExerciseRoom> room = rooms.remove(context);
                    if (room != null && room.getValue() != null) {
                        room.getValue().close();
                    }
                    // TODO log if value null
                }
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
