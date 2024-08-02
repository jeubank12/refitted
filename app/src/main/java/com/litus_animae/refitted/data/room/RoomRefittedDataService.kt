package com.litus_animae.refitted.data.room

import android.content.Context
import android.util.Log
import androidx.room.Room

object RoomRefittedDataService {
    private const val TAG = "RoomExerciseDataService"
    private const val db_name = "dev01.2.db"

    // TODO use lazy
    @Volatile
    private var roomDatabase: RefittedRoom? = null
    fun getRefittedRoom(context: Context): RefittedRoom {
        Log.i(
            TAG,
            "getExerciseRoomAsync: context " + context
                .toString() + " requested Room database, " + Thread.currentThread().name
        )
        if (roomDatabase == null) {
            Log.d(TAG, "getExerciseRoomAsync: waiting for exclusive access")
            synchronized(this) {
                Log.d(TAG, "getExerciseRoomAsync: got exclusive access")
                if (roomDatabase == null) {
                    Log.d(TAG, "getExerciseRoomAsync: creating room connection")
                    roomDatabase = Room.databaseBuilder(
                        context.applicationContext,
                        RefittedRoom::class.java, db_name
                    )
                        .addMigrations(
                            RefittedRoom.MIGRATION_1_2,
                            RefittedRoom.MIGRATION_2_3,
                            RefittedRoom.MIGRATION_3_4,
                            RefittedRoom.MIGRATION_4_5,
                            RefittedRoom.MIGRATION_5_6,
                            RefittedRoom.MIGRATION_6_7,
                            RefittedRoom.MIGRATION_7_8,
                            RefittedRoom.MIGRATION_8_9,
                            RefittedRoom.MIGRATION_9_10,
                            RefittedRoom.MIGRATION_10_11,
                            RefittedRoom.MIGRATION_11_12
                        )
                        .build()
                } else {
                    Log.d(
                        TAG,
                        "getExerciseRoomAsync: someone else already created the room connection"
                    )
                }
            }
        }
        return roomDatabase!!
    }
}