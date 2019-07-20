package com.litus_animae.refitted.data;

import android.content.Context;

import androidx.room.Room;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoomDataService {

    private static final String TAG = "RoomDataService";
    private static final String db_name = "dev01.2.db";
    private static final Map<Context, ExerciseRoom> rooms = new ConcurrentHashMap<>();

    public static ExerciseRoom GetExerciseRoom(Context context) {
        ExerciseRoom room = rooms.get(context.getApplicationContext());
        if (room == null) {
            synchronized (ExerciseRoom.class) {
                if (rooms.get(context.getApplicationContext()) == null) {
                    room = Room.databaseBuilder(context.getApplicationContext(),
                            ExerciseRoom.class, db_name).build();
                    rooms.put(context.getApplicationContext(), room);
                }
            }
        }
        // TODO how to make this thread safe, null-safe
        return room;
    }

    public static void CloseExerciseRoom(Context context) {
        ExerciseRoom room = rooms.remove(context.getApplicationContext());
        if (room != null) {
            synchronized (ExerciseRoom.class) {
                room.close();
            }
        }
    }
}
