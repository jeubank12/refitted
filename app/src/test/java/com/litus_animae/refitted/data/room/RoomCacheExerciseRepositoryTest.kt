package com.litus_animae.refitted.data.room

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.Record
import com.litus_animae.refitted.models.RoomExerciseSet
import com.litus_animae.refitted.models.SetRecord
import com.litus_animae.refitted.util.LogUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

@OptIn(FlowPreview::class)
@ExperimentalCoroutinesApi
class RoomCacheExerciseRepositoryTest {

  private lateinit var subject: RoomCacheExerciseRepository

  // Mocks
  private val roomProvider: RefittedRoomProvider = mockk()
  private val networkService: ExerciseSetNetworkService = mockk()
  private val roomDatabase: RefittedRoom = mockk()
  private val exerciseDao: ExerciseDao = mockk()
  private val log: LogUtil = mockk(relaxed = true)

  // Test Data
  private val workoutName = "TestWorkout"
  private val exercise =
    flowOf(mockk<Exercise>()) // We don't need the details of the Exercise itself

  private val simpleRoomSet = RoomExerciseSet(
    workout = workoutName,
    day = "1",
    step = "1",
    primaryStep = 1,
    superSetStep = null,
    alternateStep = null,
    name = "Chest_Bench Press",
    note = "",
    reps = 10,
    sets = 3,
    isToFailure = false,
    rest = 60,
    repsUnit = "reps",
    repsRange = 0,
    timeLimit = null,
    timeLimitUnit = null,
    repsSequence = emptyList()
  )
  private val simpleExerciseSet = ExerciseSet(simpleRoomSet, exercise)

  @BeforeEach
  fun setUp() {
    every { roomProvider.refittedRoom } returns roomDatabase
    every { roomDatabase.getExerciseDao() } returns exerciseDao

    subject = RoomCacheExerciseRepository(roomProvider, networkService, log)
  }

  fun setupGetSetRecords(
    time: Instant,
    targetExerciseSet: ExerciseSet,
    expectedRecords: List<SetRecord>
  ) {
    every {
      exerciseDao.getSetRecords(
        time,
        targetExerciseSet.exerciseName,
        targetExerciseSet.id
      )
    } returns flowOf(expectedRecords)
  }


  @Nested
  @DisplayName("getCurrentRecords")
  inner class GetCurrentRecords {
    @Test
    fun `returns an empty list with no result`() = runTest {
      // Given
      val runTime = Instant.now()
      setupGetSetRecords(runTime, simpleExerciseSet, emptyList())

      // When
      val result = subject.getCurrentRecords(runTime, simpleExerciseSet)

      // Then
      result.test {
        assertThat(awaitItem()).isEmpty()
        cancelAndIgnoreRemainingEvents()
      }
    }

    @Test
    fun `returns a single record`() = runTest {
      // Given
      val runTime = Instant.now()
      val completionTime = runTime.minus(10, ChronoUnit.MINUTES)
      val record = SetRecord(
        weight = 110.0,
        reps = 10,
        simpleExerciseSet,
      ).copy(completed = completionTime)
      setupGetSetRecords(runTime, simpleExerciseSet, listOf(record))

      // When
      val result = subject.getCurrentRecords(runTime, simpleExerciseSet)

      // Then
      val expectedRecord = Record(
        record.weight,
        record.reps,
        simpleExerciseSet,
        completionTime,
        record.reps,
        stored = true
      )

      result.test {
        assertThat(awaitItem()).isEqualTo(listOf(expectedRecord))
        cancelAndIgnoreRemainingEvents()
      }
    }

    @Test
    fun `returns records with accumulated reps`() = runTest {
      // Given
      val runTime = Instant.now()
      val completionTime1 = runTime.minus(20, ChronoUnit.MINUTES)
      val completionTime2 = runTime.minus(10, ChronoUnit.MINUTES)
      val record = SetRecord(
        weight = 110.0,
        reps = 10,
        simpleExerciseSet,
      )
      val record1 = record.copy(completed = completionTime1)
      val record2 = record.copy(reps = 5, weight = 100.0, completed = completionTime2)
      setupGetSetRecords(runTime, simpleExerciseSet, listOf(record1, record2))

      // When
      val result = subject.getCurrentRecords(runTime, simpleExerciseSet)

      // Then
      val expectedRecord1 = Record(
        record1.weight,
        record1.reps,
        simpleExerciseSet,
        completionTime1,
        record1.reps,
        stored = true
      )
      val expectedRecord2 = Record(
        record2.weight,
        record2.reps,
        simpleExerciseSet,
        completionTime2,
        record2.reps + record1.reps,
        stored = true
      )

      result.test {
        assertThat(awaitItem()).isEqualTo(listOf(expectedRecord1, expectedRecord2))
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  @Nested
  @DisplayName("buildExerciseRecord")
  inner class BuildExerciseRecord {
    @Test
    fun `returns default record for input targetExerciseSet`() = runTest {
      // Given
      val runTime = Instant.now()
      setupGetSetRecords(runTime, simpleExerciseSet, emptyList())
      // empty table with nullable results returns null
      every { exerciseDao.getLatestSetRecord(simpleExerciseSet.exerciseName) } returns flowOf(null)

      // When
      val result = subject.buildExerciseRecord(simpleExerciseSet, runTime)

      // Then
      assertThat(result.targetSet).isEqualTo(simpleExerciseSet)
      assertThat(result.defaultRecord.set).isEqualTo(simpleExerciseSet)
    }

    @Test
    fun `returns no latest record with no result`() = runTest {
      // Given
      val runTime = Instant.now()
      setupGetSetRecords(runTime, simpleExerciseSet, emptyList())
      // empty table with nullable results returns null
      every { exerciseDao.getLatestSetRecord(simpleExerciseSet.exerciseName) } returns flowOf(null)

      // When
      val result = subject.buildExerciseRecord(simpleExerciseSet, runTime)

      // Then
      result.latestRecord.test {
        awaitComplete()
      }
    }

    @Test
    fun `returns stored record from same time as latest`() = runTest {
      // Given
      val runTime = Instant.now()
      val completionTime = runTime.minus(10, ChronoUnit.MINUTES)
      val record = SetRecord(
        weight = 110.0,
        reps = 10,
        simpleExerciseSet,
      ).copy(completed = completionTime)
      setupGetSetRecords(runTime, simpleExerciseSet, listOf(record))
      every { exerciseDao.getLatestSetRecord(simpleExerciseSet.exerciseName) } returns flowOf(record)

      // When
      val result = subject.buildExerciseRecord(simpleExerciseSet, runTime)

      // Then
      val expectedRecord = Record(
        record.weight,
        record.reps,
        simpleExerciseSet,
        completionTime,
        record.reps,
        stored = true
      )

      result.currentRecords.test {
        assertThat(awaitItem()).isEqualTo(listOf(expectedRecord))
        cancelAndIgnoreRemainingEvents()
      }

      result.latestRecord.test {
        assertThat(awaitItem()).isEqualTo(expectedRecord)
        cancelAndIgnoreRemainingEvents()
      }
    }

    @Test
    fun `returns unstored record from different time as latest`() = runTest {
      // Given
      val runTime = Instant.now()
      val completionTime = runTime.minus(10, ChronoUnit.MINUTES)
      val record = SetRecord(
        weight = 110.0,
        reps = 8,
        simpleExerciseSet,
      ).copy(completed = completionTime)
      setupGetSetRecords(runTime, simpleExerciseSet, emptyList())
      every { exerciseDao.getLatestSetRecord(simpleExerciseSet.exerciseName) } returns flowOf(record)

      // When
      val result = subject.buildExerciseRecord(simpleExerciseSet, runTime)

      // Then
      val expectedRecord = Record(
        record.weight,
        // same targetExercise copies the reps from the latest record
        record.reps,
        simpleExerciseSet,
        completionTime,
        0,
        stored = false
      )

      result.currentRecords.test {
        assertThat(awaitItem()).isEmpty()
        cancelAndIgnoreRemainingEvents()
      }

      result.latestRecord.test {
        assertThat(awaitItem()).isEqualTo(expectedRecord)
        cancelAndIgnoreRemainingEvents()
      }
    }
    // TODO test buildNewDayUnstoredRecord directly to test different day behavior
  }

  @Nested
  @DisplayName("addRecord")
  inner class AddRecord {
    @Test
    fun `calls DAO to insert a new record`() = runTest {
      // Given
      coEvery { exerciseDao.storeExerciseRecord(any()) } returns Unit
      val newRecord = SetRecord(
        weight = 110.0,
        reps = 10,
        simpleExerciseSet
      )

      // When
      subject.storeSetRecord(newRecord)

      // Then
      coVerify { exerciseDao.storeExerciseRecord(newRecord) }
    }
  }
}