package com.litus_animae.refitted.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.*
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.maybeZipWithNext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@FlowPreview
@HiltViewModel
class ExerciseViewModel @Inject constructor(
  private val exerciseRepo: ExerciseRepository,
  private val log: LogUtil
) : ViewModel() {
  val exercises =
    exerciseRepo.exercises
      .map { sets ->
        log.i(TAG, "Received new set of exercises: $sets")
        val instructions = sets.groupBy { it.primaryStep }
          .map { NonEmptyList.fromList(it.value) }
          .flattenOption()
          .maybeZipWithNext { thisSets, nextSets ->
            if (thisSets.head.isSuperSet) {
              if (nextSets?.head?.isSuperSet == true) {
                ExerciseInstruction(thisSets, Some(1))
              } else {
                ExerciseInstruction(thisSets, thisSets.head.superSetStep.map { it * -1 })
              }
            } else {
              ExerciseInstruction(thisSets, None)
            }
          }
        if (instructions.isNotEmpty()) {
          log.d(TAG, "Finished Loading")
        }
        log.i(TAG, "Processed set of exercises to: $instructions")
        instructions
      }

  fun recordForExercise(set: ExerciseSet): Flow<ExerciseRecord> {
    return exerciseRepo.records.mapNotNull { allRecords ->
      allRecords.firstOrNull {
        it.targetSet.id == set.id
      }
    }
  }

  data class ExerciseInstruction(
    val sets: NonEmptyList<ExerciseSet>,
    val offsetToNextSuperSet: Option<Int>
  ) {
    val hasAlternate = sets.size > 1
    val prefix = sets.head.superStep
    val alternateCount = sets.size
    private val _activeIndex = MutableStateFlow(0)
    val activeIndex = _activeIndex.asStateFlow()
    fun activateNextAlternate() {
      if (_activeIndex.value < sets.size - 1) {
        _activeIndex.value += 1
      } else {
        _activeIndex.value = 0
      }
    }

    fun activateAlternate(index: Int) {
      if (index in 0 until alternateCount)
        _activeIndex.value = index
      else if (index < 0) _activeIndex.value = 0
      else _activeIndex.value = alternateCount - 1
    }

    val set = _activeIndex.map { sets.getOrElse(it) { sets.head } }
  }

  val isLoading = exerciseRepo.exercisesAreLoading

  fun loadExercises(day: String, workoutId: String) {
    try {
      viewModelScope.launch {
        exerciseRepo.loadExercises(day, workoutId)
      }
    } catch (ex: Throwable) {
      // TODO error state
      log.e(TAG, "error loading exercises", ex)
    }
  }

  fun refreshExercises() {
    exerciseRepo.refreshExercises()
  }

  fun saveExercise(record: SetRecord) {
    viewModelScope.launch {
      exerciseRepo.storeSetRecord(record)
    }
  }

  companion object {
    private const val TAG = "ExerciseViewModel"
  }
}