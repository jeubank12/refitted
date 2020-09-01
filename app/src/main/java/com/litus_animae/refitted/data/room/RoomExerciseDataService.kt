package com.litus_animae.refitted.data.room

import android.content.Context
import android.util.Log
import androidx.room.Room
import java.lang.ref.WeakReference

object RoomExerciseDataService {
    private const val TAG = "RoomExerciseDataService"
    private const val db_name = "dev01.2.db"

    @Volatile
    private var roomDatabase: ExerciseRoom? = null
    fun getExerciseRoom(context: WeakReference<Context>): ExerciseRoom {
        Log.i(TAG, "getExerciseRoomAsync: context " + context.get().toString() + " requested Room database, " + Thread.currentThread().name)
        if (roomDatabase == null) {
            Log.d(TAG, "getExerciseRoomAsync: waiting for exclusive access")
            synchronized(this) {
                Log.d(TAG, "getExerciseRoomAsync: got exclusive access")
                if (roomDatabase == null) {
                    Log.d(TAG, "getExerciseRoomAsync: creating room connection")
                    roomDatabase = Room.databaseBuilder(context.get()!!,
                            ExerciseRoom::class.java, db_name)
                            .addMigrations(ExerciseRoom.MIGRATION_1_2, ExerciseRoom.MIGRATION_2_3, ExerciseRoom.MIGRATION_3_4)
                            .build()
                } else {
                    Log.d(TAG, "getExerciseRoomAsync: someone else already created the room connection")
                }
            }
        }
        return roomDatabase!!
    }
}