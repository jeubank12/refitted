package com.litus_animae.refitted.models

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.*
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.maybeZipWithNext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
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
  val exercises =
    exerciseRepo.exercises
      .combine(exerciseRepo.records) { sets, records ->
        val recordsByPrimaryStep = records.groupBy { it.targetSet.primaryStep }
        log.i(TAG, "Received new set of exercises: $sets")
        val instructions = sets.groupBy { it.primaryStep }
          .map { NonEmptyList.fromList(it.value) }
          .flattenOption()
          .maybeZipWithNext { thisSets, nextSets ->
            val mostRecentAlternateStep =
              getLastCompletedAlternateIndex(thisSets, recordsByPrimaryStep)

            if (thisSets.head.isSuperSet) {
              val nextSet = nextSets?.head
              if (nextSet?.isSuperSet == true && nextSet.superStep == thisSets.head.superStep) {
                ExerciseInstruction(thisSets, Some(1), mostRecentAlternateStep)
              } else {
                ExerciseInstruction(
                  thisSets,
                  thisSets.head.superSetStep.map { it * -1 },
                  mostRecentAlternateStep
                )
              }
            } else {
              ExerciseInstruction(thisSets, None, mostRecentAlternateStep)
            }
          }
        if (instructions.isNotEmpty()) {
          log.d(TAG, "Finished Loading")
        }
        log.i(TAG, "Processed set of exercises to: $instructions")
        instructions
      }
      .catch {
        log.e(TAG, "There was an error loading exercises", it)
        exercisesError = "There was an error loading exercises"
      }

  private fun getLastCompletedAlternateIndex(
    thisSets: NonEmptyList<ExerciseSet>,
    recordsByPrimaryStep: Map<String, List<ExerciseRecord>>
  ): Flow<Int> {
    log.v(TAG, "Sets $thisSets has records ${recordsByPrimaryStep[thisSets.head.primaryStep]}")
    val mostRecentRecordStep = flowOf(recordsByPrimaryStep[thisSets.head.primaryStep])
      .onStart { emit(emptyList()) }
      .flatMapLatest { instructionRecords ->
        val storedRecords = instructionRecords.orEmpty().map {
          it.latestRecord
            .filter { record -> record.stored }
            .take(1)
        }
        merge(*storedRecords.toTypedArray())
          .runningFold(emptyList<LatestRecord>()) { acc, nextRecord ->
            log.v(
              TAG,
              "Observed ${nextRecord.set} completed at ${nextRecord.completed}; as part of $acc"
            )
            acc + LatestRecord(nextRecord.set, nextRecord.completed)
          }
      }.map { lrs ->
        val latestRecordStep = lrs.maxByOrNull { it.completed }?.targetSet?.step
        val latestIndex = thisSets.indexOfFirst { it.step == latestRecordStep }
        log.v(TAG, "Seeing if $latestRecordStep is a step in sets $thisSets")
        latestIndex
      }
    return mostRecentRecordStep
  }

  val records = exerciseRepo.records

  data class LatestRecord(val targetSet: ExerciseSet, val completed: Instant)

  data class ExerciseInstruction(
    val sets: NonEmptyList<ExerciseSet>,
    val offsetToNextSuperSet: Option<Int>,
    val initialSetIndex: Flow<Int>
  ) {
    val hasAlternate = sets.size > 1
    val alternateCount = sets.size
    private val _activeIndex = MutableStateFlow(-1)
    private val _viewedIndex = MutableStateFlow(0)

    fun activeIndex(overrideIndex: Int? = null): Flow<Int> {
      return _activeIndex
        .combine(initialSetIndex.onStart { emit(-1) }) { idx, lastCompletedIdx ->
          Log.d(
            TAG,
            "Checking ${sets.head.primaryStep} active index: override $overrideIndex, mutable $idx, lastCompleted $lastCompletedIdx"
          )
          val currentIndex =
            overrideIndex ?: if (idx < 0) lastCompletedIdx.coerceAtLeast(0)
            else idx
          _viewedIndex.value = currentIndex
          currentIndex
        }.distinctUntilChanged()
    }

    fun activateNextAlternate(): Int {
      val currentValue = _viewedIndex.value
      val updatedValue = if (currentValue < alternateCount - 1) {
        currentValue.coerceAtLeast(0) + 1
      } else {
        0
      }
      _activeIndex.value = updatedValue
      return updatedValue
    }

    fun activateAlternate(index: Int) {
      _activeIndex.value = index.coerceIn(0, alternateCount - 1)
    }

    fun set(overrideIndex: Int? = null): Flow<ExerciseSet> {
      return activeIndex(overrideIndex).map { sets.getOrElse(it) { sets.head } }
    }

    override fun toString(): String {
      return "Instruction:${sets.head.primaryStep}(sets: $sets, activeIndex:${_activeIndex.value})"
    }
  }

  val isLoading = exerciseRepo.exercisesAreLoading

  fun loadExercises(day: String, workoutId: String) {
    try {
      viewModelScope.launch {
        exerciseRepo.loadExercises(day, workoutId)
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