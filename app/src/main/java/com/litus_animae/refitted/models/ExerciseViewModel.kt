package com.litus_animae.refitted.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.NonEmptyList
import arrow.core.flattenOption
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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
          .map { ExerciseInstruction(it) }
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

  class ExerciseInstruction(
    val sets: NonEmptyList<ExerciseSet>
  ) {
    val hasAlternate = sets.size > 1
    val prefix = sets.head.primaryStep
    val alternateCount = sets.size
    private val activeIndex = MutableStateFlow(0)
    fun activateNextAlternate() {
      if (activeIndex.value < sets.size - 1) {
        activeIndex.value += 1
      } else {
        activeIndex.value = 0
      }
    }

    fun activateAlternate(index: Int) {
      if (index in 0 until alternateCount)
        activeIndex.value = index
      else if (index < 0) activeIndex.value = 0
      else activeIndex.value = alternateCount - 1
    }

    val set = activeIndex.map { sets.getOrElse(it) { sets.head } }
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