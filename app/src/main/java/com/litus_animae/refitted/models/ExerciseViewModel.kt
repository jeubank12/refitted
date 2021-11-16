package com.litus_animae.refitted.models

import androidx.lifecycle.*
import arrow.core.NonEmptyList
import arrow.core.flattenOption
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.max

@FlowPreview
@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val exerciseRepo: ExerciseRepository,
    private val log: LogUtil
) : ViewModel() {
    private val currentExerciseIndex = MutableStateFlow(0)
    private val exercises =
        exerciseRepo.exercises.map { sets ->
            log.d(TAG, "Received new set of exercises: $sets")
            val instructions = sets.groupBy { it.primaryStep }
                .map { NonEmptyList.fromList(it.value) }
                .flattenOption()
                .map { ExerciseInstruction(it) }
            log.d(TAG, "Processed set of exercises to: $instructions")
            instructions
        }

    val canMoveLeft =
        currentExerciseIndex.map { it > 0 }

    fun moveLeft() {
        currentExerciseIndex.update { max(it - 1, 0) }
    }

    val canMoveRight = exercises.combine(currentExerciseIndex) { primaryExercises, idx ->
        idx < primaryExercises.size - 1
    }

    fun moveRight() {
        currentExerciseIndex.update{ max(it + 1, 0) }
    }

    val exerciseSet = currentExerciseIndex.combineTransform(exercises) { idx, sets ->
        log.d(TAG, "Saw updated currentExerciseIndex $idx")
        val currentSet = sets.getOrNull(idx)?.set ?: return@combineTransform
        log.d(TAG, "Current set is now: $currentSet")
        emit(currentSet)
    }.flattenConcat()

    val exercise = exerciseSet.flatMapConcat {
        log.d(TAG, "Getting exercise for ${it.id}")
        it.exercise
    }

    private class ExerciseInstruction(
        private val sets: NonEmptyList<ExerciseSet>
    ) {
        val hasAlternate = sets.size > 1
        val prefix = sets.head.primaryStep
        private val activeIndex = MutableStateFlow(1)
        val set = activeIndex.map { sets.getOrElse(it) { sets.head } }
    }

    suspend fun loadExercises(day: String, workoutId: String) {
//        _isLoadingBool.value = true
//        startLoad = Instant.now()
        try {
            exerciseRepo.loadExercises(day, workoutId)
        } catch (ex: Throwable) {
            log.e(TAG, "error loading exercises", ex)
        }
    }

    companion object {
        private const val TAG = "ExerciseViewModel"
    }
}