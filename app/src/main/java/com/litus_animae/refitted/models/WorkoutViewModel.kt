package com.litus_animae.refitted.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.data.WorkoutPlanRepository
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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

    val savedStateLastWorkoutPlan: String? = savedStateHandle.get<String?>("selectedPlan")
    val savedStateLastWorkoutDay: Int? = savedStateHandle.get<Int?>("lastDay")
    val lastWorkout: Flow<String?> =
        savedStateHandle.get<String?>("selectedPlan")?.let {
            flowOf(it)
        } ?: savedStateRepo.getState("selectedPlan").map { it?.value }

    suspend fun loadWorkoutDaysCompleted(workoutId: String) {
        savedStateHandle.set("selectedPlan", workoutId)
        exerciseRepo.loadWorkoutRecords(workoutId)
        savedStateRepo.setState("selectedPlan", workoutId)
    }

    val workouts: Flow<PagingData<WorkoutPlan>> = workoutPlanRepo.workouts

    val currentWorkoutPlan = MutableStateFlow(savedStateLastWorkoutPlan?.let{
        plan -> savedStateLastWorkoutDay?.let{day -> WorkoutPlan(plan, day)}
    })
    val storedStateWorkoutPlan = savedStateRepo.getState("selectedPlan")
        .filterNotNull()
        .flatMapLatest { workoutPlanRepo.workoutByName(it.value) }
    val currentWorkout = currentWorkoutPlan.combine(storedStateWorkoutPlan){
        savedState, storedState -> if (savedState == storedState) savedState else if (storedState == null) savedState else storedState
    }
}