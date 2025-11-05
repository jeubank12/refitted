package com.litus_animae.refitted.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
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

  data class ExerciseScreenState(
    val isLoading: Boolean = true,
    val instructions: List<ExerciseInstruction> = emptyList(),
    val records: List<ExerciseRecord> = emptyList(),
    val activeAlternateIndices: Map<String, Int> = emptyMap(),
    val error: String? = null
  )

  private val _activeAlternateIndices = MutableStateFlow<Map<String, Int>>(emptyMap())

  val screenState: StateFlow<ExerciseScreenState> = combine(
    exerciseRepo.exercises,
    exerciseRepo.records,
    exerciseRepo.workoutRecords,
    exerciseRepo.exercisesAreLoading,
    _activeAlternateIndices
  ) { sets, records, workoutRecords, isLoading, activeIndices ->
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

    if (instructions.isNotEmpty()) {
      log.d(TAG, "Finished Loading")
    }
    log.i(TAG, "Processed set of exercises to: $instructions")

    // The screen is loading if the repo says it's loading, or if we have exercises but are still waiting for the records.
    val screenIsLoading = isLoading || (sets.isNotEmpty() && records.size != sets.size)

    log.i(
      TAG,
      "Loading check: repoIsLoading=$isLoading, sets.isNotEmpty=${sets.isNotEmpty()}, records.size=${records.size}, sets.size=${sets.size}, condition=${records.size != sets.size}, finalIsLoading=$screenIsLoading"
    )

    ExerciseScreenState(
      screenIsLoading,
      instructions,
      records,
      initialIndices + activeIndices
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
    _activeAlternateIndices.update { currentIndices ->
      currentIndices.toMutableMap().apply { this[primaryStep] = index }
    }
  }

  fun loadExercises(day: String, workoutId: String) {
    try {
      viewModelScope.launch {
        exerciseRepo.loadExercises(day, workoutId)
      }
      viewModelScope.launch {
        // we probably will want a per-day version of this to speed up load. We don't need the full history unless we open the menu
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

  fun saveExercise(record: SetRecord) {
    try {
      viewModelScope.launch {
        exerciseRepo.storeSetRecord(record)
      }
    } catch (ex: Throwable) {
      log.e(TAG, "error storing set record", ex)
      exercisesError = "There was an error storing the set record"
    }
  }

  companion object {
    private const val TAG = "ExerciseViewModel"
  }
}
