package com.litus_animae.refitted.compose.state

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.paging.PagingData
import arrow.core.Option
import arrow.core.getOrElse
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.SetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

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
    if (setRecords.firstOrNull { !it.stored } != null)
      setRecords[setRecords.lastIndex] = savedRecord
    else
      setRecords.add(savedRecord)
  }
}

@Composable
fun recordsByExerciseId(allRecords: List<ExerciseRecord>): Map<String, ExerciseSetWithRecord> {
  return allRecords.associateBy { it.targetSet.id }
    .mapValues { currentSetEntry ->
      val currentSetRecord = currentSetEntry.value
      val currentSet = currentSetRecord.targetSet
      val rememberedSetRecords = remember { mutableStateListOf<Record>() }
      // TODO update default weight with best default
      val defaultRecord by derivedStateOf {
        Record(
          25.0,
          if (currentSet.repsUnit.isNotBlank()) 0 else currentSet.reps,
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
        todayExerciseRecords.map {
          Record(it.weight, it.reps, currentSet, stored = true)
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
