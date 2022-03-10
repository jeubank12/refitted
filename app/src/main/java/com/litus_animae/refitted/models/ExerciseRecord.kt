package com.litus_animae.refitted.models

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExerciseRecord(
    val targetSet: ExerciseSet,
    val latestSet: Flow<SetRecord?>,
    val allSets: Flow<PagingData<SetRecord>>,
    val sets: Flow<List<SetRecord>>
) {
    fun getSet(set: Int): Flow<SetRecord?> {
        return sets.map { sets: List<SetRecord> ->
            if (set < sets.size && set >= 0) {
                sets[set]
            } else if (set < 0 && sets.size + set >= 0) {
                sets[sets.size + set]
            } else null
        }
    }

    val setsCount = sets.map { sets -> sets.size }
}