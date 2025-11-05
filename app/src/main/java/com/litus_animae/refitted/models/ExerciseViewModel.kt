package com.litus_animae.refitted.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import com.litus_animae.refitted.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.maybeZipWithNext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@FlowPreview
@HiltViewModel
class ExerciseViewModel @Inject constructor(
  private val exerciseRepo: ExerciseRepository,
  private val log: LogUtil
) : ViewModel() {
  var exercisesError: String? by mutableStateOf(null)
    private set

  private data class UiState(
    val activeAlternateIndices: Map<String, Int> = emptyMap(),
    val editedRecordSetId: String? = null,
    val editedRecord: Record? = null
  )

  data class ExerciseScreenState(
    val isLoading: Boolean = true,
    val instructions: List<ExerciseInstruction> = emptyList(),
    val setRecords: Map<String, ExerciseSetWithRecord> = emptyMap(),
    val activeAlternateIndices: Map<String, Int> = emptyMap(),
    val editedRecord: Record? = null,
    val error: String? = null
  )

  private val _uiState = MutableStateFlow(UiState())

  val screenState: StateFlow<ExerciseScreenState> = combine(
    exerciseRepo.exercises,
    exerciseRepo.records,
    exerciseRepo.workoutRecords,
    exerciseRepo.exercisesAreLoading,
    _uiState
  ) { sets, records, workoutRecords, isLoading, uiState ->
    log.i(TAG, "Received new set of exercises: $sets")
    val instructions = sets.groupBy { it.primaryStep }
      .mapNotNull { it.value.toNonEmptyListOrNull() }
      .maybeZipWithNext { thisSets, nextSets ->
        val offsetToNextSuperSet = if (thisSets.head.isSuperSet) {
          val nextSet = nextSets?.head
          if (nextSet?.isSuperSet == true && nextSet.superStep == thisSets.head.superStep) {
            1
          } else {
            thisSets.head.superSetStep?.let { it * -1 }
          }
        } else {
          null
        }
        ExerciseInstruction(thisSets, offsetToNextSuperSet)
      }

    val completionTimes = workoutRecords.associate { it.dayAndSet to it.latestCompletion }
    val initialIndices = instructions.associate {
      val primaryStep = it.sets.head.primaryStep
      val lastCompletedSet = it.sets.maxByOrNull { set -> completionTimes[set.id] ?: Instant.MIN }
      val index = it.sets.indexOf(lastCompletedSet).coerceAtLeast(0)
      primaryStep to index
    }

    val setRecords = records.associate {
      val defaultRecord = Record(
        weight = 25.0, // TODO: Better default
        reps = it.targetSet.reps(it.todaysRecords.size),
        set = it.targetSet,
        completed = Instant.ofEpochMilli(0)
      )
      it.targetSet.id to ExerciseSetWithRecord(
        exerciseSet = it.targetSet,
        latestRecord = it.latestRecord ?: defaultRecord,
        todaysRecords = it.todaysRecords,
        allSets = it.allSets
      )
    }

    val recordSetIds = records.map { it.targetSet.id }.toSet()
    val screenIsLoading =
      isLoading || (sets.isNotEmpty() && !recordSetIds.containsAll(sets.map { it.id }.toSet()))

    log.i(
      TAG,
      "Loading check: repoIsLoading=$isLoading, sets=${sets.size}, records=${records.size} screenIsLoading=$screenIsLoading"
    )

    ExerciseScreenState(
      isLoading = screenIsLoading,
      instructions = instructions,
      setRecords = setRecords,
      activeAlternateIndices = initialIndices + uiState.activeAlternateIndices,
      editedRecord = uiState.editedRecord
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = ExerciseScreenState()
  )


  data class ExerciseInstruction(
    val sets: NonEmptyList<ExerciseSet>,
    val offsetToNextSuperSet: Int?
  ) {
    val hasAlternate = sets.size > 1
    val alternateCount = sets.size

    override fun toString(): String {
      return "Instruction:${sets.head.primaryStep}(sets: $sets)"
    }
  }

  fun onAlternateSelected(primaryStep: String, index: Int) {
    _uiState.update { it.copy(activeAlternateIndices = it.activeAlternateIndices + (primaryStep to index)) }
  }

  fun selectSetToEdit(setWithRecord: ExerciseSetWithRecord) {
    val recordToEdit = setWithRecord.latestRecord.copy(stored = false)
    _uiState.update {
      it.copy(
        editedRecordSetId = setWithRecord.exerciseSet.id,
        editedRecord = recordToEdit
      )
    }
  }

  fun onRepsChange(reps: Int) {
    _uiState.update { it.copy(editedRecord = it.editedRecord?.copy(reps = reps)) }
  }

  fun onWeightChange(weight: Double) {
    _uiState.update { it.copy(editedRecord = it.editedRecord?.copy(weight = weight)) }
  }

  fun saveCurrentRecord(): Record? {
    val recordToSave = _uiState.value.editedRecord?.copy(stored = true)
    if (recordToSave != null) {
      viewModelScope.launch {
        exerciseRepo.storeSetRecord(
          SetRecord(
            recordToSave.weight,
            recordToSave.reps,
            recordToSave.set
          )
        )
      }
    }
    // TODO select next record to edit
    _uiState.update { it.copy(editedRecord = null, editedRecordSetId = null) }
    return recordToSave
  }

  fun loadExercises(day: String, workoutId: String) {
    try {
      viewModelScope.launch {
        exerciseRepo.loadExercises(day, workoutId)
      }
      viewModelScope.launch {
        exerciseRepo.loadWorkoutRecords(workoutId)
      }
    } catch (ex: Throwable) {
      log.e(TAG, "error loading exercises", ex)
      exercisesError = "There was an error loading exercises"
    }
  }

  fun refreshExercises() {
    exerciseRepo.refreshExercises()
  }

  companion object {
    private const val TAG = "ExerciseViewModel"
  }
}
