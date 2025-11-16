package com.litus_animae.refitted.data.room

import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.models.SavedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class RoomSavedStateRepository @Inject constructor(private val roomProvider: RefittedRoomProvider) :
  SavedStateRepository {
  private val refittedRoom = flow { emit(roomProvider.refittedRoom) }
    .flowOn(Dispatchers.IO)

  override fun getState(key: String): Flow<SavedState?> {
    return refittedRoom.flatMapLatest { it.getSavedStateDao().getState(key) }
  }

  override suspend fun setState(key: String, value: String) {
    refittedRoom.last().getSavedStateDao().insert(SavedState(key, value))
  }
}