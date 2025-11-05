package com.litus_animae.refitted.compose.state

import androidx.paging.PagingData
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.Record
import com.litus_animae.refitted.models.SetRecord
import kotlinx.coroutines.flow.Flow

// This class is now a simple, stateless data holder.
// The stateful parts (like the currently edited record) will be managed in the ViewModel.
data class ExerciseSetWithRecord(
    val exerciseSet: ExerciseSet,
    val latestRecord: Record, // The most recent record for this set (from any day)
    val todaysRecords: List<Record>, // All records for this set from today
    val allSets: Flow<PagingData<SetRecord>> // Pager for historical records
) {
    val numCompleted = todaysRecords.size

    val reps = exerciseSet.reps(numCompleted)

    val exerciseIncomplete = numCompleted < exerciseSet.sets ||
        (exerciseSet.sets < 0 && latestRecord.cumulativeReps < reps) ||
        // "challenge" type exercise without a count-of-reps end state
        (exerciseSet.sets < 0 && reps < 0)
}
