package com.litus_animae.refitted.data.room

import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.models.SavedState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RoomSavedStateRepository @Inject constructor(private val refittedRoom: RefittedRoom) :
    SavedStateRepository {
    override val state: Flow<List<SavedState>> = refittedRoom.getSavedStateDao().allState()
    override fun getState(key: String): Flow<SavedState?> {
        return refittedRoom.getSavedStateDao().getState(key)
    }

    override suspend fun setState(key: String, value: String) {
        refittedRoom.getSavedStateDao().insert(SavedState(key, value))
    }
}