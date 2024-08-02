package com.litus_animae.refitted.data

import com.litus_animae.refitted.models.SavedState
import kotlinx.coroutines.flow.Flow

interface SavedStateRepository {
    fun getState(key: String): Flow<SavedState?>
    suspend fun setState(key: String, value: String)
}