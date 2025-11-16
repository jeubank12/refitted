package com.litus_animae.refitted.room

import android.content.Context
import android.util.Log
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class RefittedRoomProviderLive @Inject constructor(
  @ApplicationContext context: Context
) : RefittedRoomProvider {
  companion object {
    private const val db_name = "dev01.2.db"
    private const val TAG = "RefittedRoomProviderLive"
  }

  override val refittedRoom: RefittedRoom by lazy {
    Log.i(TAG, "building Room database on ${Thread.currentThread().name}")
    Room.databaseBuilder(
      context,
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
  }
}