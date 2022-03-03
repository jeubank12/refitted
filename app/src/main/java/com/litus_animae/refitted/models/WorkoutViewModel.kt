package com.litus_animae.refitted.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.data.WorkoutPlanRepository
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.*
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val exerciseRepo: ExerciseRepository,
    private val log: LogUtil,
    private val workoutPlanRepo: WorkoutPlanRepository,
    private val savedStateRepo: SavedStateRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    val completedDays: Flow<Map<Int, Date>> =
        exerciseRepo.workoutRecords.map { records ->
            records.groupBy { it.day }
                .mapValues { entry -> entry.value.maxOf { it.latestCompletion } }
                .mapKeys { it.key.toIntOrNull() ?: 0 }
        }

    fun getSavedStateLastWorkout(): String? { return savedStateHandle.get<String?>("selectedPlan") }
    fun getLastWorkout(): Flow<String?> {
        return savedStateHandle.get<String?>("selectedPlan")?.let {
            flowOf(it)
        } ?: savedStateRepo.getState("selectedPlan").map { it?.value }
    }

    suspend fun loadWorkoutDaysCompleted(workoutId: String) {
        savedStateHandle.set("selectedPlan", workoutId)
        exerciseRepo.loadWorkoutRecords(workoutId)
        savedStateRepo.setState("selectedPlan", workoutId)
    }

    val workouts: Flow<PagingData<WorkoutPlan>> = workoutPlanRepo.workouts
}