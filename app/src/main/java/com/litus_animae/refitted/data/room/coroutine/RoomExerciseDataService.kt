package com.litus_animae.refitted.data.room.coroutine

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.litus_animae.refitted.data.room.ExerciseRoom
import com.litus_animae.refitted.data.room.asynctask.RoomExerciseDataService
import java.lang.ref.WeakReference

object RoomExerciseDataService {
    private const val TAG = "RoomExerciseDataService"

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
                            ExerciseRoom::class.java, RoomExerciseDataService.db_name)
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