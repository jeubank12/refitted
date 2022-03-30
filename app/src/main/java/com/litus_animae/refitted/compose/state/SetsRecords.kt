package com.litus_animae.refitted.compose.state

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.paging.PagingData
import arrow.core.Option
import arrow.core.getOrElse
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.SetRecord
import com.litus_animae.refitted.util.maybeZipWithPrevious
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import java.lang.Integer.min

data class ExerciseSetWithRecord(
  val exerciseSet: ExerciseSet,
  val currentRecord: Record,
  val numCompleted: Int,
  val setRecords: SnapshotStateList<Record>,
  val allSets: Flow<PagingData<SetRecord>>
) {
  fun saveRecordInState(
    savedRecord: Record
  ) {
    if (setRecords.firstOrNull { !it.stored } != null) {
      val recordToSave =
        if (savedRecord.stored) savedRecord.copy(cumulativeReps = savedRecord.cumulativeReps + savedRecord.reps)
        else savedRecord
      setRecords[setRecords.lastIndex] = recordToSave
    }
    else
      setRecords.add(savedRecord)
  }

  val exerciseIncomplete = numCompleted < exerciseSet.sets ||
    (exerciseSet.sets < 0 && currentRecord.cumulativeReps < exerciseSet.reps)
}

@Composable
fun recordsByExerciseId(allRecords: List<ExerciseRecord>): Map<String, ExerciseSetWithRecord> {
  return allRecords.associateBy { it.targetSet.id }
    .mapValues { currentSetEntry ->
      val currentSetRecord = currentSetEntry.value
      val currentSet = currentSetRecord.targetSet
      val rememberedSetRecords = remember { mutableStateListOf<Record>() }
      val defaultReps = when {
        currentSet.repsUnit.isNotBlank() -> 0
        currentSet.sets < 0 -> min(10, currentSet.reps)
        else -> currentSet.reps
      }
      val defaultRecord by derivedStateOf {
        Record(
          weight = 25.0,
          defaultReps,
          currentSet
        )
      }
      val lastExerciseRecord by currentSetRecord.latestSet.collectAsState(
        initial = null,
        Dispatchers.IO
      )
      val todayExerciseRecords by currentSetRecord.sets.collectAsState(
        initial = emptyList(),
        Dispatchers.IO
      )
      val todayRecords by derivedStateOf {
        todayExerciseRecords.maybeZipWithPrevious { a, b ->
          Record(b.weight, b.reps, currentSet, b.reps + (a?.reps ?: 0), stored = true)
        }
      }
      val lastStoredRecord by derivedStateOf {
        Option.fromNullable(lastExerciseRecord).map {
          Record(it.weight, it.reps, currentSet)
        }.getOrElse { defaultRecord }
      }
      val setRecords by derivedStateOf {
        if (rememberedSetRecords.isEmpty()) {
          if (todayRecords.isEmpty()) mutableStateListOf(lastStoredRecord)
          else mutableStateListOf(*todayRecords.toTypedArray())
        } else rememberedSetRecords
      }
      val currentRecord by derivedStateOf {
        val unsavedRecord = setRecords.firstOrNull { !it.stored }
        val lastTodayRecord = todayRecords.lastOrNull()
        val lastRecord = setRecords.last()
        val lastRecordUpdatedForToday =
          if (currentSet.reps < 0) lastRecord
          else lastRecord.copy(reps = currentSet.reps)
        unsavedRecord ?: lastTodayRecord ?: lastRecordUpdatedForToday
      }
      val setsCompleted by currentSetRecord.setsCount.collectAsState(initial = 0, Dispatchers.IO)
      ExerciseSetWithRecord(
        currentSet,
        currentRecord,
        setsCompleted,
        rememberedSetRecords,
        currentSetRecord.allSets
      )
    }
}
