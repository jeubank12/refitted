package com.litus_animae.refitted.models

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.data.WorkoutPlanRepository
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

@ExperimentalCoroutinesApi
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val exerciseRepo: ExerciseRepository,
    private val log: LogUtil,
    private val workoutPlanRepo: WorkoutPlanRepository,
    private val savedStateRepo: SavedStateRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val selectedPlan = "selectedPlan"
    private val selectedPlanDays = "selectedPlanDays"
    private val lastDay = "lastDay"

    val savedStateLastWorkoutPlan: WorkoutPlan? by lazy {
        val plan = hydratePlan(
            savedStateHandle.get<String?>(selectedPlan),
            savedStateHandle.get<Int?>(selectedPlanDays),
            savedStateHandle.get<Int?>(lastDay)
        )
        log.d("WorkoutViewModel", "Loaded saved state plan $plan")
        plan
    }
    private val storedStateLastWorkoutPlan = combine(
        savedStateRepo.getState(selectedPlan).map { it?.value }.distinctUntilChanged(),
        savedStateRepo.getState(selectedPlanDays).map { it?.value }.distinctUntilChanged(),
        savedStateRepo.getState(lastDay).map { it?.value }.distinctUntilChanged()) { n, t, p ->
        val plan = hydratePlan(n, t, p)
        log.d("WorkoutViewModel", "Loaded stored state from db $plan")
        plan
    }

    var completedDaysLoading by mutableStateOf(false)
        private set
    var savedStateLoading by mutableStateOf(savedStateLastWorkoutPlan == null)
        private set
    private val _currentWorkout by lazy{ MutableStateFlow(savedStateLastWorkoutPlan) }
    private val savedWorkout = flow {
        val savedPlan = savedStateLastWorkoutPlan
        savedStateLoading = savedPlan == null
        emit(savedStateLastWorkoutPlan)
        storedStateLastWorkoutPlan.collect {
            savedStateLoading = false
            if (it != null) emit(it)
        }
    }.distinctUntilChanged()
    val currentWorkout = combine(_currentWorkout, savedWorkout){
        current, saved -> current ?: saved
    }.distinctUntilChanged()

    private fun hydratePlan(name: String?, totalDays: String?, lastDay: String?): WorkoutPlan? {
        return hydratePlan(name, totalDays?.toIntOrNull(), lastDay?.toIntOrNull())
    }

    private fun hydratePlan(name: String?, totalDays: Int?, lastDay: Int?): WorkoutPlan? {
        return name?.let { n -> totalDays?.let { t -> lastDay?.let { WorkoutPlan(n, t, it) } } }
    }

    val completedDays: Flow<Map<Int, Date>> =
        currentWorkout.map{it?.workout}
            .distinctUntilChanged()
            .flatMapLatest { maybePlan ->
            maybePlan?.let{plan ->
                completedDaysLoading = true
                Log.d("WorkoutViewModel", "Loading completed days for $plan")
                exerciseRepo.loadWorkoutRecords(plan) }
            exerciseRepo.workoutRecords.map { records ->
                completedDaysLoading = false
                records.groupBy { it.day }
                    .mapValues { entry -> entry.value.maxOf { it.latestCompletion } }
                    .mapKeys { it.key.toIntOrNull() ?: 0 }
            }
        }

    suspend fun loadWorkoutDaysCompleted(workout: WorkoutPlan) {
        Log.d("WorkoutViewModel", "Setting currentWorkout $workout")
        _currentWorkout.value = workout
        log.d("WorkoutViewModel", "Saving selected plan $workout")
        savedStateHandle.set(selectedPlan, workout.workout)
        savedStateHandle.set(selectedPlanDays, workout.totalDays)
        savedStateHandle.set(lastDay, workout.lastViewedDay)
        withContext(Dispatchers.IO) {
            savedStateRepo.setState(selectedPlan, workout.workout)
            savedStateRepo.setState(lastDay, workout.lastViewedDay.toString())
            savedStateRepo.setState(selectedPlanDays, workout.totalDays.toString())
            workoutPlanRepo.workoutByName(workout.workout).first()?.let { newWorkoutPlan ->
                if (newWorkoutPlan != workout) {
                    log.w("WorkoutViewModel", "Updating saved selected plan with new values $newWorkoutPlan")
                    savedStateRepo.setState(lastDay, newWorkoutPlan.lastViewedDay.toString())
                    savedStateHandle.set(lastDay, newWorkoutPlan.lastViewedDay)

                    savedStateRepo.setState(selectedPlanDays, newWorkoutPlan.totalDays.toString())
                    savedStateHandle.set(selectedPlanDays, newWorkoutPlan.totalDays)
                }
            }
        }
    }

    suspend fun setLastViewedDay(workout: WorkoutPlan, day: Int) {
        Log.d("WorkoutViewModel", "Setting last viewed day $day")
        savedStateHandle.set(lastDay, day)
        withContext(Dispatchers.IO){
            savedStateRepo.setState(lastDay, day.toString())
            workoutPlanRepo.setWorkoutLastViewedDay(workout, day)
        }
    }

    val workouts: Flow<PagingData<WorkoutPlan>> = workoutPlanRepo.workouts

}