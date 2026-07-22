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
      val newDay = subject.copyCustomDay(existingPlan.toDomain(), fromDay = 1, toDay = null)

      // Then - a new day 3, with sets/reps filled from what was actually completed
      assertThat(newDay).isEqualTo(3)
      assertThat(stored.captured).containsExactly(
        sourceExercise.copy(day = "3", sets = 2, reps = 8)
      )
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
      subject.copyCustomDay(existingPlan.toDomain(), fromDay = 1, toDay = null)

      // Then
      assertThat(stored.captured).containsExactly(sourceExercise.copy(day = "3"))
    }

    @Test
    fun `overwrites an existing day and leaves totalDays unchanged`() = runTest {
      // Given
      val existingPlan = RoomWorkoutPlan(workout = workoutName, totalDays = 3, isCustom = true)
      coEvery { workoutPlanDao.getByName(workoutName) } returns existingPlan
      coEvery { exerciseDao.loadDayExerciseSets("1", workoutName) } returns listOf(sourceExercise)
      coEvery { exerciseDao.loadDaySetRecords(workoutName, "1") } returns emptyList()
      coEvery { exerciseDao.clearDay("2", workoutName) } returns Unit
      coEvery { exerciseDao.storeExerciseSets(any()) } returns Unit

      // When
      val newDay = subject.copyCustomDay(existingPlan.toDomain(), fromDay = 1, toDay = 2)

      // Then
      assertThat(newDay).isEqualTo(2)
      coVerify { exerciseDao.clearDay("2", workoutName) }
      coVerify(exactly = 0) { workoutPlanDao.update(any()) }
    }
  }
}
