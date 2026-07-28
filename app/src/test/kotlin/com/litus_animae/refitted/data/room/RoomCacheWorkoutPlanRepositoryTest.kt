package com.litus_animae.refitted.data.room

import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.data.network.WorkoutPlanNetworkService
import com.litus_animae.refitted.room.ExerciseDao
import com.litus_animae.refitted.room.RefittedRoom
import com.litus_animae.refitted.room.RefittedRoomProvider
import com.litus_animae.refitted.room.WorkoutPlanDao
import com.litus_animae.refitted.room.entities.RoomExerciseSet
import com.litus_animae.refitted.room.entities.RoomSetRecord
import com.litus_animae.refitted.room.entities.RoomWorkoutPlan
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.TestLogUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@ExperimentalCoroutinesApi
class RoomCacheWorkoutPlanRepositoryTest {

  private lateinit var subject: RoomCacheWorkoutPlanRepository

  private val roomProvider: RefittedRoomProvider = mockk()
  private val networkService: WorkoutPlanNetworkService = mockk()
  private val roomDatabase: RefittedRoom = mockk()
  private val workoutPlanDao: WorkoutPlanDao = mockk()
  private val exerciseDao: ExerciseDao = mockk()
  private val log: LogUtil = TestLogUtil

  private val workoutName = "My Custom Plan"

  @BeforeEach
  fun setUp() {
    every { roomProvider.refittedRoom } returns roomDatabase
    every { roomDatabase.getWorkoutPlanDao() } returns workoutPlanDao
    every { roomDatabase.getExerciseDao() } returns exerciseDao
    every { workoutPlanDao.update(any()) } returns Unit
    every { workoutPlanDao.getServerPlans() } returns emptyFlow()

    subject = RoomCacheWorkoutPlanRepository(roomProvider, networkService, log)
  }

  @Nested
  @DisplayName("createCustomPlan")
  inner class CreateCustomPlan {
    @Test
    fun `inserts an already-aligned custom plan and returns it`() = runTest {
      // Given
      val inserted = slot<List<RoomWorkoutPlan>>()
      coEvery { workoutPlanDao.insertAll(capture(inserted)) } returns Unit

      // When
      val result = subject.createCustomPlan(workoutName)

      // Then
      assertThat(result.workout).isEqualTo(workoutName)
      assertThat(result.isCustom).isTrue()
      assertThat(result.totalDays).isEqualTo(0)
      // Aligned means non-epoch, unlike an admin plan awaiting a start-date pick
      assertThat(result.workoutStartDate).isNotEqualTo(Instant.ofEpochMilli(0))
      assertThat(inserted.captured).containsExactly(RoomWorkoutPlan.fromDomain(result))
    }
  }

  @Nested
  @DisplayName("addDayToCustomPlan")
  inner class AddDayToCustomPlan {
    @Test
    fun `increments totalDays and persists it`() = runTest {
      // Given
      val existingPlan = RoomWorkoutPlan(workout = workoutName, totalDays = 2, isCustom = true)
      coEvery { workoutPlanDao.getByName(workoutName) } returns existingPlan

      // When
      val newDay = subject.addDayToCustomPlan(existingPlan.toDomain())

      // Then
      assertThat(newDay).isEqualTo(3)
      coVerify { workoutPlanDao.update(existingPlan.copy(totalDays = 3)) }
    }

    @Test
    fun `falls back to the passed-in plan when it isn't in the DB yet`() = runTest {
      // Given
      coEvery { workoutPlanDao.getByName(workoutName) } returns null
      val plan = WorkoutPlan(workoutName, totalDays = 1, isCustom = true)

      // When
      val newDay = subject.addDayToCustomPlan(plan)

      // Then
      assertThat(newDay).isEqualTo(2)
    }
  }

  @Nested
  @DisplayName("copyCustomDay")
  inner class CopyCustomDay {
    private val sourceExercise = RoomExerciseSet(
      workout = workoutName,
      day = "1",
      step = "1",
      primaryStep = 1,
      superSetStep = null,
      alternateStep = null,
      name = "custom_Push-Up",
      note = "",
      reps = -1,
      sets = -1,
      isToFailure = false,
      rest = 90,
      repsUnit = "",
      repsRange = 0,
      timeLimit = null,
      timeLimitUnit = null,
      repsSequence = emptyList()
    )

    @Test
    fun `appends a new day with targets derived from completed sets`() = runTest {
      // Given
      val existingPlan = RoomWorkoutPlan(workout = workoutName, totalDays = 2, isCustom = true)
      coEvery { workoutPlanDao.getByName(workoutName) } returns existingPlan
      coEvery { exerciseDao.loadDayExerciseSets("1", workoutName) } returns listOf(sourceExercise)
      coEvery { exerciseDao.loadDaySetRecords(workoutName, "1") } returns listOf(
        RoomSetRecord(25.0, 9, workoutName, "1.1", Instant.ofEpochMilli(1), "custom_Push-Up"),
        RoomSetRecord(25.0, 8, workoutName, "1.1", Instant.ofEpochMilli(2), "custom_Push-Up")
      )
      val stored = slot<List<RoomExerciseSet>>()
      coEvery { exerciseDao.storeExerciseSets(capture(stored)) } returns Unit

      // When
      val newDay = subject.copyCustomDay(existingPlan.toDomain(), fromDay = 1)

      // Then - a new day 3, with sets/reps filled from what was actually completed
      assertThat(newDay).isEqualTo(3)
      assertThat(stored.captured).containsExactly(
        sourceExercise.copy(day = "3", sets = 2, reps = 8)
      )
      // Copy is append-only - it must never touch an existing day's rows.
      coVerify(exactly = 0) { exerciseDao.clearDay(any(), any()) }
      coVerify { workoutPlanDao.update(existingPlan.copy(totalDays = 3)) }
    }

    @Test
    fun `copies as still-open sets when nothing was completed`() = runTest {
      // Given
      val existingPlan = RoomWorkoutPlan(workout = workoutName, totalDays = 2, isCustom = true)
      coEvery { workoutPlanDao.getByName(workoutName) } returns existingPlan
      coEvery { exerciseDao.loadDayExerciseSets("1", workoutName) } returns listOf(sourceExercise)
      coEvery { exerciseDao.loadDaySetRecords(workoutName, "1") } returns emptyList()
      val stored = slot<List<RoomExerciseSet>>()
      coEvery { exerciseDao.storeExerciseSets(capture(stored)) } returns Unit

      // When
      subject.copyCustomDay(existingPlan.toDomain(), fromDay = 1)

      // Then
      assertThat(stored.captured).containsExactly(sourceExercise.copy(day = "3"))
    }
  }

  @Nested
  @DisplayName("addRestDayToCustomPlan")
  inner class AddRestDayToCustomPlan {
    @Test
    fun `appends a new day and marks it as rest`() = runTest {
      // Given
      val existingPlan = RoomWorkoutPlan(workout = workoutName, totalDays = 2, isCustom = true)
      coEvery { workoutPlanDao.getByName(workoutName) } returns existingPlan

      // When
      val newDay = subject.addRestDayToCustomPlan(existingPlan.toDomain())

      // Then
      assertThat(newDay).isEqualTo(3)
      coVerify { workoutPlanDao.update(existingPlan.copy(totalDays = 3, restDays = listOf(3))) }
    }
  }

  @Nested
  @DisplayName("clearCustomDay")
  inner class ClearCustomDay {
    @Test
    fun `deletes the day's exercises without touching totalDays or restDays`() = runTest {
      // Given
      coEvery { exerciseDao.clearDay("2", workoutName) } returns Unit
      val plan = WorkoutPlan(workoutName, totalDays = 3, isCustom = true)

      // When
      subject.clearCustomDay(plan, day = 2)

      // Then
      coVerify { exerciseDao.clearDay("2", workoutName) }
      coVerify(exactly = 0) { workoutPlanDao.update(any()) }
    }
  }

  @Nested
  @DisplayName("setCustomDayRest")
  inner class SetCustomDayRest {
    @Test
    fun `marking a day as rest adds it to restDays and clears its exercises`() = runTest {
      // Given
      val existingPlan =
        RoomWorkoutPlan(workout = workoutName, totalDays = 3, isCustom = true, restDays = emptyList())
      coEvery { workoutPlanDao.getByName(workoutName) } returns existingPlan
      coEvery { exerciseDao.clearDay("2", workoutName) } returns Unit

      // When
      subject.setCustomDayRest(existingPlan.toDomain(), day = 2, isRest = true)

      // Then
      coVerify { workoutPlanDao.update(existingPlan.copy(restDays = listOf(2))) }
      coVerify { exerciseDao.clearDay("2", workoutName) }
    }

    @Test
    fun `removing rest drops the day from restDays without touching exercises`() = runTest {
      // Given
      val existingPlan =
        RoomWorkoutPlan(workout = workoutName, totalDays = 3, isCustom = true, restDays = listOf(2))
      coEvery { workoutPlanDao.getByName(workoutName) } returns existingPlan

      // When
      subject.setCustomDayRest(existingPlan.toDomain(), day = 2, isRest = false)

      // Then
      coVerify { workoutPlanDao.update(existingPlan.copy(restDays = emptyList())) }
      coVerify(exactly = 0) { exerciseDao.clearDay(any(), any()) }
    }
  }
}
