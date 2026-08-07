package com.litus_animae.refitted.data

import com.litus_animae.refitted.data.device.SetRecordSink
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.room.RefittedRoomProvider
import com.litus_animae.refitted.room.entities.RoomSetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `@Singleton`, unlike [com.litus_animae.refitted.data.room.RoomCacheExerciseRepository] which is
 * `@ViewModelComponent`-scoped - a watch listener runs outside any ViewModel's lifetime and needs
 * a writer that's always available. `RoomCacheExerciseRepository.storeSetRecord` delegates here
 * too, so the phone path and the watch path share one writer.
 */
@Singleton
class RoomSetRecordSink @Inject constructor(
  private val roomProvider: RefittedRoomProvider
) : SetRecordSink {
  override suspend fun store(records: List<SetRecord>) {
    withContext(Dispatchers.IO) {
      val exerciseDao = roomProvider.refittedRoom.getExerciseDao()
      records.forEach { exerciseDao.storeExerciseRecord(RoomSetRecord.fromDomain(it)) }
    }
  }
}
