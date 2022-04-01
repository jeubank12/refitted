package com.litus_animae.refitted.compose.state

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.paging.PagingData
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.orElse
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.SetRecord
import com.litus_animae.refitted.util.maybeZipWithPrevious
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import java.lang.Integer.min

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
    (exerciseSet.sets < 0 && currentRecord.value.cumulativeReps < exerciseSet.reps)
}

@Composable
fun recordsByExerciseId(allRecords: List<ExerciseRecord>): Map<String, ExerciseSetWithRecord> {
  val state = remember { mutableStateMapOf<String, ExerciseSetWithRecord>() }
  allRecords.associateBy { it.targetSet.id }
    .forEach { currentSetEntry ->
      val currentSetRecord = currentSetEntry.value
      val currentSet = currentSetRecord.targetSet
      val todayRecords by getTodayRecords(exerciseRecord = currentSetRecord)
      val unsavedRecord by createFirstUnsavedRecord(
        currentSetRecord,
        latestCurrentSetRecord = todayRecords.lastOrNull()
      )
      val currentRecord = remember { mutableStateOf(unsavedRecord) }
      val rememberedSetRecords = remember { mutableStateListOf(*todayRecords.toTypedArray()) }
      val setsCompleted by currentSetRecord.setsCount.collectAsState(initial = 0, Dispatchers.IO)
      state[currentSet.id] = ExerciseSetWithRecord(
        currentSet,
        currentRecord,
        setsCompleted,
        rememberedSetRecords,
        currentSetRecord.allSets
      )
    }
  return state
}

/**
 * Given an exercise set, create a default "first" record
 */
@Composable
private fun getDefaultRecord(exerciseSet: ExerciseSet): State<Record> {
  return derivedStateOf {
    val defaultReps = when {
      exerciseSet.repsUnit.isNotBlank() -> 0
      exerciseSet.sets < 0 -> min(10, exerciseSet.reps)
      else -> exerciseSet.reps
    }
    Record(
      weight = 25.0,
      defaultReps,
      exerciseSet
    )
  }
}

/**
 *
 */
@Composable
private fun createFirstUnsavedRecord(
  exerciseRecord: ExerciseRecord,
  latestCurrentSetRecord: Record?
): State<Record> {
  /** A default record when there is nothing in remembered state */
  val defaultSetRecord by getDefaultRecord(exerciseSet = exerciseRecord.targetSet)

  /** latest exercise record from the database */
  val latestExerciseRecord by exerciseRecord.latestSet.collectAsState(
    initial = null,
    Dispatchers.IO
  )
  val lastStoredRecord = derivedStateOf {
    Option.fromNullable(latestCurrentSetRecord).map {
      // copy the latest record and mark it as not-stored
      it.copy(stored = false)
    }.orElse {
      Option.fromNullable(latestExerciseRecord).map {
        // TODO should this do the reset of reps to match current set?
        Record(it.weight, it.reps, exerciseRecord.targetSet)
      }
    }.getOrElse { defaultSetRecord } // otherwise take the default
  }
  return lastStoredRecord
}

/**
 * Today's exercise records from [ExerciseRecord],
 * converted into [Record] state holder
 */
@Composable
private fun getTodayRecords(exerciseRecord: ExerciseRecord): State<List<Record>> {
  /** all set records for today */
  val todayExerciseRecords by exerciseRecord.currentSets.collectAsState(
    initial = emptyList(),
    Dispatchers.IO
  )

  /** today's records converted to state holders */
  val todayRecords = derivedStateOf {
    // TODO this doesn't appear to be accumulating correctly
    todayExerciseRecords.maybeZipWithPrevious { a, b ->
      Record(b.weight, b.reps, exerciseRecord.targetSet, b.reps + (a?.reps ?: 0), stored = true)
    }
  }
  return todayRecords
}
