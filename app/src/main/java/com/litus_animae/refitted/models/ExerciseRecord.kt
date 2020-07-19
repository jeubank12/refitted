package com.litus_animae.refitted.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.paging.DataSource

class ExerciseRecord(val targetSet: ExerciseSet,
                     val latestSet: LiveData<SetRecord>,
                     val allSets: DataSource.Factory<Int, SetRecord>,
                     val sets: LiveData<List<SetRecord>>) {
    fun getSet(set: Int): LiveData<SetRecord?> {
        return Transformations.map(sets) { sets: List<SetRecord> ->
            if (set < sets.size && set >= 0) {
                return@map sets[set]
            } else if (set < 0 && sets.size + set >= 0) {
                return@map sets[sets.size + set]
            }
            null
        }
    }

    val setsCount: LiveData<Int>
        get() = Transformations.map(sets) { sets: List<SetRecord?>? ->
            if (sets != null) {
                return@map sets.size
            }
            0
        }

}