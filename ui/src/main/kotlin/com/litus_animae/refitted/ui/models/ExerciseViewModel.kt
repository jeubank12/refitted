package com.litus_animae.refitted.ui.models

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.WorkoutPlanRepository
import com.litus_animae.refitted.data.device.WatchPlan
import com.litus_animae.refitted.data.device.WatchService
import com.litus_animae.refitted.data.device.WatchState
import com.litus_animae.refitted.data.device.buildWatchPlan
import com.litus_animae.refitted.data.models.Exercise
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.MuscleGroup
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.maybeZipWithNext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@FlowPreview
@HiltViewModel
class ExerciseViewModel @Inject constructor(
  private val exerciseRepo: ExerciseRepository,
  private val workoutPlanRepo: WorkoutPlanRepository,
  private val watchService: WatchService,
  private val log: LogUtil
) : ViewModel() {
  var exercisesError: String? by mutableStateOf(null)
    private set
  // Shared so per-instruction flows and the UI collect one repo subscription between them —
  // a fresh collector replays the cached list instead of triggering a records reload.
  val records = exerciseRepo.records
    .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

  // Which alternate is selected per step, keyed by primaryStep. Held here rather than on
  // ExerciseInstruction so a user's choice survives the list rebuild that any edit on the day
  // triggers - ExerciseInstruction instances are recreated wholesale on every such rebuild.
  private val alternateSelections = mutableMapOf<String, AlternateSelection>()

  // Instructions are rebuilt only when the exercise list itself changes; record updates flow
  // through each instruction's initialSetIndex instead of recreating instruction state.
  // stateIn shares one instance across all collectors (pager cards, timer, menu).
  val exercises =
    exerciseRepo.exercises
      .distinctUntilChanged()
      .map { sets ->
        log.i(TAG, "Received new set of exercises: $sets")
        val instructions = sets.groupBy { it.primaryStep }
          .map { it.value.toNonEmptyListOrNull() }
          .filterNotNull()
          .maybeZipWithNext { thisSets, nextSets ->
            // stateIn so all cards observing this instruction share one records pipeline; a
            // resubscribing card gets the cached index immediately with no repo round-trip
            val mostRecentAlternateStep = getLastCompletedAlternateIndex(thisSets)
              .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)
            val selection = alternateSelections.getOrPut(thisSets.head.primaryStep) {
              AlternateSelection()
            }

            if (thisSets.head.isSuperSet) {
              val nextSet = nextSets?.head
              if (nextSet?.isSuperSet == true && nextSet.superStep == thisSets.head.superStep) {
                ExerciseInstruction(thisSets, 1, mostRecentAlternateStep, selection)
              } else {
                ExerciseInstruction(
                  thisSets,
                  thisSets.head.superSetStep?.let { it * -1 },
                  mostRecentAlternateStep,
                  selection
                )
              }
            } else {
              ExerciseInstruction(thisSets, null, mostRecentAlternateStep, selection)
            }
          }
        if (instructions.isNotEmpty()) {
          log.d(TAG, "Finished Loading")
        }
        log.i(TAG, "Processed set of exercises to: $instructions")
        instructions
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val watchState: StateFlow<WatchState> = watchService.state

  // The rest timer arbitration lives here (rather than being read directly off watchState by the
  // UI) because Top.kt scopes ViewModels per nav destination - this state must outlive navigation.
  val watchSessionActive: StateFlow<Boolean> = watchState
    .map { it is WatchState.Active }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  init {
    // GarminWatchService never discovers a device on its own - without this, watchState sits at
    // its initial NoDevice forever and sendPlanToWatch silently no-ops.
    refreshWatchState()
  }

  fun refreshWatchState() {
    viewModelScope.launch {
      watchService.refresh()
    }
  }

  fun sendPlanToWatch(globalAlternate: Int?) {
    viewModelScope.launch {
      try {
        watchService.startSession(resolveWatchPlan(globalAlternate)).onFailure { ex ->
          log.e(TAG, "error sending plan to watch", ex)
        }
      } catch (ex: Throwable) {
        log.e(TAG, "error building watch plan", ex)
      }
    }
  }

  /**
   * Resolves the currently displayed instructions - `.set(globalAlternate)` is the same call
   * [PagerExerciseInstructions] uses to decide what to display, so the watch always shows exactly
   * what the phone shows - then hands them to [buildWatchPlan] for the (independently tested)
   * flattening into the wire shape.
   */
  private suspend fun resolveWatchPlan(globalAlternate: Int?): WatchPlan {
    val instructions = exercises.value
    val currentRecords = records.first()
    val resolvedSets = instructions.map { it.set(globalAlternate).first() }
    val firstSet = instructions.firstOrNull()?.sets?.head
    // suggestedWeight must be synchronous, so latestRecord (a Flow) is resolved up front here
    // rather than inside buildWatchPlan's lambda. latestRecord never emits at all for a set with
    // no history anywhere (RoomCacheExerciseRepository.buildExerciseRecord's combine().mapNotNull{}
    // drops the all-null case rather than completing), so first() is bounded by a timeout to fall
    // through to defaultRecord instead of hanging sendPlanToWatch forever.
    val suggestedWeights = resolvedSets.associate { set ->
      val exerciseRecord = currentRecords.firstOrNull { it.targetSet.id == set.id }
      val weight = exerciseRecord?.let { record ->
        withTimeoutOrNull(RESOLVE_LATEST_WEIGHT_TIMEOUT_MS) { record.latestRecord.first().weight }
      } ?: exerciseRecord?.defaultRecord?.weight
        ?: 0.0
      set.id to weight
    }
    return buildWatchPlan(
      workout = firstSet?.workout.orEmpty(),
      day = firstSet?.day.orEmpty(),
      resolvedSets = resolvedSets
    ) { set -> suggestedWeights[set.id] ?: 0.0 }
  }

  private fun getLastCompletedAlternateIndex(thisSets: NonEmptyList<ExerciseSet>): Flow<Int> {
    val primaryStep = thisSets.head.primaryStep
    return records
      .onStart { emit(emptyList()) }
      .map { records -> records.filter { it.targetSet.primaryStep == primaryStep } }
      .distinctUntilChanged()
      .flatMapLatest { instructionRecords ->
        val storedRecords = instructionRecords.map {
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
        log.v(
          TAG,
          "Last completed alternate for step $primaryStep: ${latestRecordStep ?: "none"} (index $latestIndex)"
        )
        latestIndex
      }
  }

  // Per-exercise timer sticky state — survives pager swipes and rotation
  data class TimerState(
    val isRunning: Boolean,
    val startedAt: Instant = Instant.now(),
    /** Rest duration in seconds, stored so the ring renders correctly even when viewing another exercise. */
    val restSeconds: Int = 0
  )
  val timerStateByExerciseId: SnapshotStateMap<String, TimerState> = mutableStateMapOf()

  fun setTimerRunning(id: String, running: Boolean, restSeconds: Int = 0) {
    if (running) {
      // Only one timer active at a time — cancel any other running timers first
      timerStateByExerciseId.keys.toList().forEach { key ->
        if (key != id && timerStateByExerciseId[key]?.isRunning == true) {
          timerStateByExerciseId[key] = TimerState(isRunning = false)
        }
      }
      timerStateByExerciseId[id] = TimerState(isRunning = true, startedAt = Instant.now(), restSeconds = restSeconds)
    } else {
      timerStateByExerciseId[id] = TimerState(isRunning = false)
    }
  }

  data class LatestRecord(val targetSet: ExerciseSet, val completed: Instant)

  // Holds a step's alternate choice outside any single ExerciseInstruction instance, so it
  // survives the wholesale instance rebuild that an edit anywhere on the day triggers.
  class AlternateSelection {
    val activeIndex = MutableStateFlow(-1)
    val viewedIndex = MutableStateFlow(0)
  }

  data class ExerciseInstruction(
    val sets: NonEmptyList<ExerciseSet>,
    val offsetToNextSuperSet: Int?,
    val initialSetIndex: Flow<Int>,
    private val selection: AlternateSelection
  ) {
    val hasAlternate = sets.size > 1
    val alternateCount = sets.size

    fun activeIndex(overrideIndex: Int? = null): Flow<Int> {
      return selection.activeIndex
        .combine(initialSetIndex.onStart { emit(-1) }) { idx, lastCompletedIdx ->
          val currentIndex =
            overrideIndex ?: if (idx < 0) lastCompletedIdx.coerceAtLeast(0)
            else idx
          Log.v(
            TAG,
            "Resolved alternate for step ${sets.head.primaryStep} to index $currentIndex " +
              "(planOverride=$overrideIndex, userSelected=$idx, lastCompleted=$lastCompletedIdx)"
          )
          selection.viewedIndex.value = currentIndex
          currentIndex
        }.distinctUntilChanged()
    }

    fun activateNextAlternate(): Int {
      val currentValue = selection.viewedIndex.value
      val updatedValue = if (currentValue < alternateCount - 1) {
        currentValue.coerceAtLeast(0) + 1
      } else {
        0
      }
      selection.activeIndex.value = updatedValue
      return updatedValue
    }

    fun activateAlternate(index: Int) {
      selection.activeIndex.value = index.coerceIn(0, alternateCount - 1)
    }

    fun set(overrideIndex: Int? = null): Flow<ExerciseSet> {
      return activeIndex(overrideIndex).map { sets.getOrElse(it) { sets.head } }
    }

    override fun toString(): String {
      return "Instruction:${sets.head.primaryStep}(sets: $sets, " +
        "activeIndex:${selection.activeIndex.value})"
    }
  }

  val isLoading = exerciseRepo.exercisesAreLoading

  fun loadExercises(day: String, workoutId: String) {
    viewModelScope.launch {
      try {
        exerciseRepo.loadExercises(day, workoutId)
      } catch (ex: Throwable) {
        log.e(TAG, "error loading exercises", ex)
        exercisesError = "There was an error loading exercises"
      }
    }
  }

  fun refreshExercises() {
    exerciseRepo.refreshExercises()
  }

  fun addExercise(workout: String, day: String, exerciseId: String, description: String? = null) {
    viewModelScope.launch {
      try {
        exerciseRepo.addCustomExercise(workout, day, exerciseId, description)
      } catch (ex: Throwable) {
        log.e(TAG, "error adding custom exercise", ex)
        exercisesError = "There was an error adding the exercise"
      }
    }
  }

  fun addAlternateExercise(
    workout: String,
    day: String,
    baseStep: String,
    exerciseId: String,
    description: String? = null
  ) {
    viewModelScope.launch {
      try {
        exerciseRepo.addAlternateExercise(workout, day, baseStep, exerciseId, description)
      } catch (ex: Throwable) {
        log.e(TAG, "error adding alternate exercise", ex)
        exercisesError = "There was an error adding the alternate"
      }
    }
  }

  fun updateCustomExerciseSetTargets(
    workout: String,
    day: String,
    step: String,
    sets: Int,
    reps: Int,
    rest: Int,
    repsRange: Int
  ) {
    viewModelScope.launch {
      try {
        exerciseRepo.updateCustomExerciseSet(workout, day, step, sets, reps, rest, repsRange)
      } catch (ex: Throwable) {
        log.e(TAG, "error updating custom exercise set targets", ex)
        exercisesError = "There was an error updating the exercise"
      }
    }
  }

  fun deleteExercise(workout: String, day: String, step: String) {
    viewModelScope.launch {
      try {
        exerciseRepo.deleteCustomExerciseSet(workout, day, step)
      } catch (ex: Throwable) {
        log.e(TAG, "error deleting custom exercise set", ex)
        exercisesError = "There was an error removing the exercise"
      }
    }
  }

  fun updateCustomExerciseSetNote(workout: String, day: String, step: String, note: String) {
    viewModelScope.launch {
      try {
        exerciseRepo.updateCustomExerciseSetNote(workout, day, step, note)
      } catch (ex: Throwable) {
        log.e(TAG, "error updating custom exercise set note", ex)
        exercisesError = "There was an error updating the exercise's instructions"
      }
    }
  }

  // Add-exercise picker (muscle group browsing) - local matches are live; remote matches are
  // fetched per-workout on demand since browsing shouldn't pull every accessible plan's catalog.
  val accessibleWorkouts = workoutPlanRepo.accessibleWorkouts

  fun exercisesByMuscle(muscle: String): Flow<List<Exercise>> = exerciseRepo.exercisesByMuscle(muscle)

  // Scoped to this ViewModel's nav back-stack entry (the day being edited) - carries over
  // between successive add-exercise sheet opens, resets once the day screen itself is left.
  private val _selectedMuscle = MutableStateFlow(MuscleGroup.displayNames().first())
  val selectedMuscle: StateFlow<String> = _selectedMuscle.asStateFlow()

  fun selectMuscle(muscle: String) {
    _selectedMuscle.value = muscle
  }

  // Keyed by (workout, muscle) - not workout alone - so switching muscle within one session
  // never shows a stale result cached under the same workout for a different muscle.
  val remoteExercisesByWorkout: SnapshotStateMap<Pair<String, String>, List<Exercise>> = mutableStateMapOf()
  val loadingWorkouts: SnapshotStateMap<Pair<String, String>, Boolean> = mutableStateMapOf()

  fun loadRemoteExercises(workout: String, muscle: String) {
    val key = workout to muscle
    if (loadingWorkouts[key] == true) return
    loadingWorkouts[key] = true
    viewModelScope.launch {
      try {
        remoteExercisesByWorkout[key] = exerciseRepo.loadRemoteExercisesByMuscle(workout, muscle)
      } catch (ex: Throwable) {
        log.e(TAG, "error loading remote exercises for $workout/$muscle", ex)
        exercisesError = "There was an error loading exercises"
      } finally {
        loadingWorkouts[key] = false
      }
    }
  }

  fun saveExercise(record: SetRecord) {
    viewModelScope.launch {
      try {
        exerciseRepo.storeSetRecord(record)
      } catch (ex: Throwable) {
        log.e(TAG, "error storing set record", ex)
        exercisesError = "There was an error storing the set record"
      }
    }
  }

  companion object {
    private const val TAG = "ExerciseViewModel"
    private const val RESOLVE_LATEST_WEIGHT_TIMEOUT_MS = 500L
  }
}