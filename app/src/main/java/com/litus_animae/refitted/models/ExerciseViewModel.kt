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
    val exercises =
        exerciseRepo.exercises.map { sets ->
            log.i(TAG, "Received new set of exercises: $sets")
            val instructions = sets.groupBy { it.primaryStep }
                .map { NonEmptyList.fromList(it.value) }
                .flattenOption()
                .map { ExerciseInstruction(it) }
            log.i(TAG, "Processed set of exercises to: $instructions")
            instructions
        }

    class ExerciseInstruction(
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