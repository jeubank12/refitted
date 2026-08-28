package com.litus_animae.refitted.ui.models

import androidx.paging.PagingData
import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.WorkoutPlanRepository
import com.litus_animae.refitted.data.device.WatchPlan
import com.litus_animae.refitted.data.device.WatchService
import com.litus_animae.refitted.data.device.WatchState
import com.litus_animae.refitted.data.models.ExerciseRecord
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.util.LogUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ExerciseViewModelTest {

  private val exerciseRepo = mockk<ExerciseRepository>(relaxed = true)
  private val workoutPlanRepo = mockk<WorkoutPlanRepository>(relaxed = true)
  private val watchService = mockk<WatchService>(relaxed = true)
  private val log = mockk<LogUtil>(relaxed = true)

  private fun exerciseSet(step: String = "1") = ExerciseSet(
    workout = "Push",
    day = "1",
    step = step,
    name = "Chest_Bench Press",
    note = "",
    reps = 10,
    sets = 3,
    isToFailure = false,
    rest = 90,
    repsUnit = "reps",
    repsRange = 0,
    timeLimit = null,
    timeLimitUnit = null,
    repsSequence = emptyList(),
    exercise = flowOf(null)
  )

  private fun record(weight: Double, set: ExerciseSet) =
    Record(weight = weight, reps = 10, set = set, completed = Instant.now())

  // viewModelScope dispatches onto Dispatchers.Main.immediate, so this must be the same
  // dispatcher/scheduler runTest below drives - otherwise advanceUntilIdle() only advances the
  // test body's own scheduler and never touches coroutines viewModelScope.launch queues onto Main.
  private val mainDispatcher = StandardTestDispatcher()

  @BeforeEach
  fun setUp() {
    Dispatchers.setMain(mainDispatcher)
    every { watchService.state } returns MutableStateFlow(WatchState.NoDevice)
  }

  @AfterEach
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Nested
  @DisplayName("Sending the plan to the watch")
  inner class SendPlanToWatch {
    @Test
    fun `suggested weight comes from the latest performed record, not the hardcoded default`() = runTest(mainDispatcher) {
      val set = exerciseSet()
      // RoomCacheExerciseRepository.buildDefaultRecordForExerciseSet's hardcoded placeholder -
      // never what the user actually lifted.
      val defaultRecord = record(weight = 25.0, set = set)
      // What the phone's own weight input pre-fills from (RoomCacheExerciseRepository.buildExerciseRecord).
      val latestRecord = record(weight = 185.0, set = set)
      every { exerciseRepo.exercises } returns flowOf(listOf(set))
      every { exerciseRepo.records } returns flowOf(
        listOf(
          ExerciseRecord(
            targetSet = set,
            defaultRecord = defaultRecord,
            latestRecord = flowOf(latestRecord),
            allSets = flowOf(PagingData.empty()),
            currentRecords = flowOf(emptyList())
          )
        )
      )
      val planSlot = slot<WatchPlan>()
      coEvery { watchService.startSession(capture(planSlot)) } returns Result.success(Unit)

      val model = ExerciseViewModel(exerciseRepo, workoutPlanRepo, watchService, log)
      // exercises/records are shared StateFlows that only start collecting their upstream once
      // subscribed - collect here so resolveWatchPlan sees a resolved value, matching how the
      // phone UI is always already collecting `exercises` before this button is reachable.
      backgroundScope.launch { model.exercises.collect {} }
      advanceUntilIdle()

      model.sendPlanToWatch(null)
      advanceUntilIdle()

      assertThat(planSlot.captured.exercises.single().suggestedWeight).isEqualTo(185.0)
    }

    @Test
    fun `falls back to the default record when the exercise has never been performed`() = runTest(mainDispatcher) {
      val set = exerciseSet()
      val defaultRecord = record(weight = 25.0, set = set)
      every { exerciseRepo.exercises } returns flowOf(listOf(set))
      every { exerciseRepo.records } returns flowOf(
        listOf(
          ExerciseRecord(
            targetSet = set,
            defaultRecord = defaultRecord,
            // Mirrors RoomCacheExerciseRepository.buildExerciseRecord's real shape when a set has
            // no history anywhere: its combine().mapNotNull{} drops the all-null case rather than
            // completing, so the flow never emits and never completes.
            latestRecord = flow { awaitCancellation() },
            allSets = flowOf(PagingData.empty()),
            currentRecords = flowOf(emptyList())
          )
        )
      )
      val planSlot = slot<WatchPlan>()
      coEvery { watchService.startSession(capture(planSlot)) } returns Result.success(Unit)

      val model = ExerciseViewModel(exerciseRepo, workoutPlanRepo, watchService, log)
      backgroundScope.launch { model.exercises.collect {} }
      advanceUntilIdle()

      model.sendPlanToWatch(null)
      advanceUntilIdle()

      assertThat(planSlot.captured.exercises.single().suggestedWeight).isEqualTo(25.0)
    }
  }

  @Nested
  @DisplayName("Watch-sync dialog support")
  inner class WatchSyncDialogSupport {
    @Test
    fun `selectWatchDevice delegates to the watch service`() = runTest(mainDispatcher) {
      val model = ExerciseViewModel(exerciseRepo, workoutPlanRepo, watchService, log)

      model.selectWatchDevice("device-1")
      advanceUntilIdle()

      coVerify { watchService.selectDevice("device-1") }
    }

    @Test
    fun `previewWatchPlan resolves the same plan sendPlanToWatch would send`() = runTest(mainDispatcher) {
      val set = exerciseSet()
      every { exerciseRepo.exercises } returns flowOf(listOf(set))
      every { exerciseRepo.records } returns flowOf(emptyList())

      val model = ExerciseViewModel(exerciseRepo, workoutPlanRepo, watchService, log)
      backgroundScope.launch { model.exercises.collect {} }
      advanceUntilIdle()

      val plan = model.previewWatchPlan(null)

      assertThat(plan.exercises.single().name).isEqualTo(set.exerciseName)
    }
  }
}
