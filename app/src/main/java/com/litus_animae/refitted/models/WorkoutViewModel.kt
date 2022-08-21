package com.litus_animae.refitted.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.data.WorkoutPlanRepository
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.SavedStateKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
  private val selectedPlanStartDate = "selectedPlanStartDate"

  companion object {
    private const val TAG = "WorkoutViewModel"
  }

  val savedStateLastWorkoutPlan: WorkoutPlan? by lazy {
    val plan = hydratePlan(
      savedStateHandle.get<String?>(selectedPlan),
      savedStateHandle.get<Int?>(selectedPlanDays),
      savedStateHandle.get<Int?>(lastDay),
      savedStateHandle.get<Long?>(selectedPlanStartDate)
    )
    log.d(TAG, "Loaded saved state plan $plan")
    plan
  }
  private val storedStateLastWorkoutPlan = savedStateRepo.getState(selectedPlan)
    .map { it?.value }.distinctUntilChanged()
    .flatMapLatest {
      savedStateLoading = false
      log.d(TAG, "Loaded stored state from db: plan $it")
      if (it != null) workoutPlanRepo.workoutByName(it)
      else emptyFlow()
    }

  var completedDaysLoading by mutableStateOf(false)
    private set
  var savedStateLoading by mutableStateOf(savedStateLastWorkoutPlan == null)
    private set
  private val _currentWorkout by lazy { MutableStateFlow(savedStateLastWorkoutPlan) }
  private val savedWorkout = flow {
    val savedPlan = savedStateLastWorkoutPlan
    savedStateLoading = savedPlan == null
    emit(savedStateLastWorkoutPlan)
    emitAll(storedStateLastWorkoutPlan.mapNotNull { it })
  }.distinctUntilChanged()
  val currentWorkout = combine(_currentWorkout, savedWorkout) { current, saved ->
    saved ?: current
  }.distinctUntilChanged()
    .catch {
      log.e(TAG, "There was an error loading the current workout", it)
      workoutError = "There was an error loading this workout plan"
    }

  private fun hydratePlan(
    name: String?,
    totalDays: Int?,
    lastDay: Int?,
    startDate: Long?
  ): WorkoutPlan? {
    return name?.let { n ->
      totalDays?.let { t ->
        lastDay?.let { d ->
          startDate?.let { WorkoutPlan(n, t, d, Instant.ofEpochMilli(it)) }
        }
      }
    }
  }

  val completedDays: Flow<Map<Int, Instant>> =
    currentWorkout.map { it?.workout }
      .distinctUntilChanged()
      .flatMapLatest { maybePlan ->
        maybePlan?.let { plan ->
          completedDaysLoading = true
          log.d(TAG, "Loading completed days for $plan")
          exerciseRepo.loadWorkoutRecords(plan)
        }
        exerciseRepo.workoutRecords.map { records ->
          completedDaysLoading = false
          records.groupBy { it.day }
            .mapValues { entry -> entry.value.maxOf { it.latestCompletion } }
            .mapKeys { it.key.toIntOrNull() ?: 0 }
        }
      }

  fun loadWorkoutDaysCompleted(workout: WorkoutPlan) {
    viewModelScope.launch(Dispatchers.IO) {
      log.d(TAG, "Setting currentWorkout $workout")
      _currentWorkout.value = workout
      log.d(TAG, "Saving selected plan $workout")
      savedStateHandle.set(selectedPlan, workout.workout)
      savedStateHandle.set(selectedPlanDays, workout.totalDays)
      savedStateHandle.set(lastDay, workout.lastViewedDay)
      savedStateHandle.set(selectedPlanStartDate, workout.workoutStartDate.toEpochMilli())
      savedStateRepo.setState(selectedPlan, workout.workout)
      workoutPlanRepo.workoutByName(workout.workout).first()?.let { newWorkoutPlan ->
        if (newWorkoutPlan != workout) {
          log.w(
            TAG,
            "Updating saved selected plan with new values $newWorkoutPlan"
          )
          savedStateHandle.set(lastDay, newWorkoutPlan.lastViewedDay)

          savedStateHandle.set(selectedPlanDays, newWorkoutPlan.totalDays)
        }
      }
    }
  }

  fun setLastViewedDay(workout: WorkoutPlan, day: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      log.d(TAG, "Setting last viewed day $day")
      savedStateHandle.set(lastDay, day)
      workoutPlanRepo.setWorkoutLastViewedDay(workout, day)
    }
  }

  fun resetWorkoutCompletion(workout: WorkoutPlan) {
    viewModelScope.launch(Dispatchers.IO) {
      log.d(TAG, "Setting start date for plan $workout")
      val now = Instant.now()
      savedStateHandle.set(selectedPlanStartDate, now.toEpochMilli())
      workoutPlanRepo.setWorkoutStartDate(workout, now)
    }
  }

  var workoutError: String? by mutableStateOf(null)
    private set
  val workouts: Flow<PagingData<WorkoutPlan>> = workoutPlanRepo.workouts
    .catch {
      log.e(TAG, "There was an error loading workouts", it)
      workoutError = "There was an error loading workouts"
    }

  val workoutsLastRefreshed: Flow<String> =
    savedStateRepo.getState(SavedStateKeys.CacheTimeKey)
      .map {
        val date = it?.value?.toLongOrNull() ?: return@map "Never"
        val lastRefreshDate = Instant.ofEpochMilli(date)
          .atZone(ZoneId.systemDefault())
          .toLocalDateTime()
        lastRefreshDate.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
      }

  fun setGlobalIndexIfEnabled(workout: WorkoutPlan?, index: Int) {
    workout?.let { activeWorkout ->
      if (activeWorkout.globalAlternateLabels.isNotEmpty())
        viewModelScope.launch(Dispatchers.IO) {
          workoutPlanRepo.setWorkoutGlobalAlternate(activeWorkout, index)
        }
    }
  }
}