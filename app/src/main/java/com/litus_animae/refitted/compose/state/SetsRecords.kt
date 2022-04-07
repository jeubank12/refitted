package com.litus_animae.refitted.compose.state

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.paging.PagingData
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.Record
import com.litus_animae.refitted.models.SetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

data class ExerciseSetWithRecord(
  val exerciseSet: ExerciseSet,
  val currentRecord: MutableState<Record>,
  val numCompleted: Int,
  val setRecords: SnapshotStateList<Record>,
  val allSets: Flow<PagingData<SetRecord>>
) {
  fun saveRecordInState(
    savedRecord: Record
  ) {
    if (savedRecord.stored) {
      val recordToSave =
        savedRecord.copy(cumulativeReps = savedRecord.cumulativeReps + savedRecord.reps)
      setRecords.add(recordToSave)
      currentRecord.value = recordToSave.copy(stored = false)
    } else {
      currentRecord.value = savedRecord
    }
  }

  val exerciseIncomplete = numCompleted < exerciseSet.sets ||
    (exerciseSet.sets < 0 && currentRecord.value.cumulativeReps < exerciseSet.reps) ||
    // "challenge" type exercise without a count-of-reps end state
    (exerciseSet.sets < 0 && exerciseSet.reps < 0)
}

@Composable
fun recordsByExerciseId(allRecords: List<ExerciseRecord>): Map<String, ExerciseSetWithRecord> {
  // TODO remember through screen rotation, will probably conflict with remember currentRecord below
  val state = remember { mutableStateMapOf<String, ExerciseSetWithRecord>() }
  SideEffect {
    Log.d("recordsByExerciseId", "Processing records")
  }
  allRecords.associateBy { it.targetSet.id }
    .forEach { currentSetEntry ->
      val currentSetRecord = currentSetEntry.value

      val currentSet = currentSetRecord.targetSet
      val todayRecords by currentSetRecord.currentRecords.collectAsState(initial = emptyList())
      val latestRecord by currentSetRecord.latestRecord.collectAsState(initial = currentSetRecord.defaultRecord)

      // TODO if one already exists in the state, use it instead of this one?
      // TODO how to correctly update this..... perhaps off todayRecords changing?
      // we need to skip the first calculation cycle when the list is empty
      val currentRecord =
        remember(latestRecord) { mutableStateOf(latestRecord.copy(stored = false)) }
      val rememberedSetRecords =
        remember(todayRecords) { mutableStateListOf(*todayRecords.toTypedArray()) }
      val setsCompleted by currentSetRecord.currentRecordsCount.collectAsState(
        initial = 0,
        Dispatchers.IO
      )
      val exerciseSetWithRecord = ExerciseSetWithRecord(
        currentSet,
        currentRecord,
        setsCompleted,
        rememberedSetRecords,
        currentSetRecord.allSets
      )

      state[currentSet.id] = exerciseSetWithRecord
    }
  return state
}
