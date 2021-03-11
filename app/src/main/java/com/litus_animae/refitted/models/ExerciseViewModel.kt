package com.litus_animae.refitted.models

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.NonEmptyList
import arrow.core.extensions.nonemptylist.foldable.get
import arrow.core.getOrElse
import arrow.integrations.kotlinx.unsafeRunScoped
import arrow.syntax.collections.flatten
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val exerciseRepo: ExerciseRepository,
    private val log: LogUtil
) : ViewModel() {
    private val currentExerciseIndex = MutableLiveData(0)
    private val exercises =
        Transformations.map(exerciseRepo.exercises) { sets ->
            log.d(TAG, "Received new set of exercises: $sets")
            val instructions = sets.groupBy { it.primaryStep }
                .map { NonEmptyList.fromList(it.value) }
                .flatten()
                .map { ExerciseInstruction(it) }
            log.d(TAG, "Processed set of exercises to: $instructions")
            instructions
        }
    val exerciseSet = Transformations.switchMap(currentExerciseIndex) {
        log.d(TAG, "Saw updated currentExerciseIndex $it")
        Transformations.switchMap(exercises) { sets ->
            val currentSet = sets[it].set
            log.d(TAG, "Current set is now: $currentSet")
            currentSet
        }
    }
    val exercise = Transformations.switchMap(exerciseSet) {
        log.d(TAG, "Getting exercise for ${it.id}")
        it.exercise
    }

    private class ExerciseInstruction(
        private val sets: NonEmptyList<ExerciseSet>
    ) {
        val hasAlternate = sets.size > 1
        val prefix = sets.head.primaryStep
        private val activeIndex = MutableLiveData(1)
        val set = Transformations.map(activeIndex) { sets.get(it.toLong()).getOrElse { sets.head } }
    }

    fun loadExercises(day: String, workoutId: String) {
//        _isLoadingBool.value = true
//        startLoad = Instant.now()
        exerciseRepo.loadExercises(day, workoutId).unsafeRunScoped(viewModelScope) { res ->
            res.mapLeft { ex -> log.e(ExerciseViewModel.TAG, "error loading exercises", ex) }
        }
    }

    companion object {
        private const val TAG = "ExerciseViewModel"
    }
}