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
import kotlinx.coroutines.flow.first
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

    private val selectedPlan = "selectedPlan"
    private val selectedPlanDays = "selectedPlanDays"
    private val lastDay = "lastDay"

    val savedStateLastWorkoutPlan: WorkoutPlan? =
        savedStateHandle.get<String?>(selectedPlan)?.let { planName ->
            savedStateHandle.get<Int?>(selectedPlanDays)?.let { totalDays ->
                savedStateHandle.get<Int?>(lastDay)?.let { WorkoutPlan(planName, totalDays, it) }
            }
        }

    suspend fun loadWorkoutDaysCompleted(workoutId: String) {
        savedStateHandle.set(selectedPlan, workoutId)
        exerciseRepo.loadWorkoutRecords(workoutId)
        savedStateRepo.setState(selectedPlan, workoutId)
        val newWorkoutPlan = workoutPlanRepo.workoutByName(workoutId).first()

        savedStateRepo.setState(lastDay, newWorkoutPlan.lastViewedDay.toString())
        savedStateHandle.set(lastDay, newWorkoutPlan.lastViewedDay)

        savedStateRepo.setState(selectedPlanDays, newWorkoutPlan.totalDays.toString())
        savedStateHandle.set(selectedPlanDays, newWorkoutPlan.totalDays)
    }

    val workouts: Flow<PagingData<WorkoutPlan>> = workoutPlanRepo.workouts

}