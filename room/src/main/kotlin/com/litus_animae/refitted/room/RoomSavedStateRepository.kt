package com.litus_animae.refitted.room

import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.data.models.SavedState
import com.litus_animae.refitted.room.entities.RoomSavedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// TODO: Migrate to DataStore with protobuf for better type safety and coroutine support
@OptIn(ExperimentalCoroutinesApi::class)
class RoomSavedStateRepository @Inject constructor(private val roomProvider: RefittedRoomProvider) :
  SavedStateRepository {
  private val refittedRoom = flow { emit(roomProvider.refittedRoom) }
    .flowOn(Dispatchers.IO)

  override fun getState(key: String): Flow<SavedState?> {
    return refittedRoom.flatMapLatest { it.getSavedStateDao().getState(key) }
      .map { it?.toDomain() }
  }

  override suspend fun setState(key: String, value: String) {
    refittedRoom.last().getSavedStateDao().insert(RoomSavedState.fromDomain(SavedState(key, value)))
  }
}