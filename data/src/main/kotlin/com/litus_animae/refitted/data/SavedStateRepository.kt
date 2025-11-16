package com.litus_animae.refitted.data

import com.litus_animae.refitted.data.models.SavedState
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for simple key-value state persistence.
 */
interface SavedStateRepository {
    fun getState(key: String): Flow<SavedState?>
    suspend fun setState(key: String, value: String)
}
