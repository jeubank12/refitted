package com.litus_animae.refitted.data.room

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.data.device.SetRecordSink
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.data.models.Exercise
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.room.entities.RoomExercise
import com.litus_animae.refitted.room.entities.RoomExerciseSet
import com.litus_animae.refitted.room.entities.RoomSetRecord
import com.litus_animae.refitted.room.RefittedRoom
import com.litus_animae.refitted.room.RefittedRoomProvider
import com.litus_animae.refitted.room.ExerciseDao
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.TestLogUtil
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
  private val setRecordSink: SetRecordSink = mockk()
  private val roomDatabase: RefittedRoom = mockk()
  private val exerciseDao: ExerciseDao = mockk()
  private val log: LogUtil = TestLogUtil

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
  private val simpleExerciseSet = ExerciseSet(
    workout = workoutName,
    day = "1",
    step = "1",
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
    repsSequence = emptyList(),
    exercise = exercise
  )

  @BeforeEach
  fun setUp() {
    every { roomProvider.refittedRoom } returns roomDatabase
    every { roomDatabase.getExerciseDao() } returns exerciseDao

    subject = RoomCacheExerciseRepository(roomProvider, networkService, setRecordSink, log)
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
    } returns flowOf(expectedRecords.map { RoomSetRecord.fromDomain(it) })
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
      every { exerciseDao.getLatestSetRecord(simpleExerciseSet.exerciseName) } returns flowOf(RoomSetRecord.fromDomain(record))

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
      every { exerciseDao.getLatestSetRecord(simpleExerciseSet.exerciseName) } returns flowOf(RoomSetRecord.fromDomain(record))

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
    @Test
    fun `recentSets is not queried until collected`() = runTest {
      // Given
      val runTime = Instant.now()
      setupGetSetRecords(runTime, simpleExerciseSet, emptyList())
      every { exerciseDao.getLatestSetRecord(simpleExerciseSet.exerciseName) } returns flowOf(null)

      // When - building the record alone must not touch Room, same laziness as the allSets Pager
      subject.buildExerciseRecord(simpleExerciseSet, runTime)

      // Then
      verify(exactly = 0) { exerciseDao.getRecentSetRecords(any(), any()) }
    }

    @Test
    fun `recentSets reverses the DAO's newest-first rows to chronological order`() = runTest {
      // Given
      val runTime = Instant.now()
      setupGetSetRecords(runTime, simpleExerciseSet, emptyList())
      every { exerciseDao.getLatestSetRecord(simpleExerciseSet.exerciseName) } returns flowOf(null)
      val older = SetRecord(90.0, 8, simpleExerciseSet)
        .copy(completed = runTime.minus(2, ChronoUnit.DAYS))
      val newer = SetRecord(95.0, 8, simpleExerciseSet)
        .copy(completed = runTime.minus(1, ChronoUnit.DAYS))
      // DAO returns newest-first (order by completed desc) - 400 is
      // RoomCacheExerciseRepository's private SET_RECORD_HISTORY_LIMIT.
      every {
        exerciseDao.getRecentSetRecords(simpleExerciseSet.exerciseName, 400)
      } returns flowOf(listOf(newer, older).map { RoomSetRecord.fromDomain(it) })

      // When
      val result = subject.buildExerciseRecord(simpleExerciseSet, runTime)

      // Then
      result.recentSets.test {
        assertThat(awaitItem()).isEqualTo(listOf(older, newer))
        cancelAndIgnoreRemainingEvents()
      }
    }

    // TODO test buildNewDayUnstoredRecord directly to test different day behavior
  }

  @Nested
  @DisplayName("addRecord")
  inner class AddRecord {
    @Test
    fun `delegates to SetRecordSink`() = runTest {
      // Given
      coEvery { setRecordSink.store(any()) } returns Unit
      val newRecord = SetRecord(
        weight = 110.0,
        reps = 10,
        simpleExerciseSet
      )

      // When
      subject.storeSetRecord(newRecord)

      // Then
      coVerify { setRecordSink.store(listOf(newRecord)) }
    }
  }

  @Nested
  @DisplayName("updateSetRecord")
  inner class UpdateSetRecord {
    @Test
    fun `delegates to the DAO, not SetRecordSink`() = runTest {
      // Given
      val completed = Instant.now()
      coEvery { exerciseDao.updateSetRecord("Chest_Bench Press", completed, 120.0, 8) } returns Unit

      // When
      subject.updateSetRecord("Chest_Bench Press", completed, 120.0, 8)

      // Then
      coVerify { exerciseDao.updateSetRecord("Chest_Bench Press", completed, 120.0, 8) }
      coVerify(exactly = 0) { setRecordSink.store(any()) }
    }
  }

  @Nested
  @DisplayName("deleteSetRecord")
  inner class DeleteSetRecord {
    @Test
    fun `delegates to the DAO`() = runTest {
      // Given
      val completed = Instant.now()
      coEvery { exerciseDao.deleteSetRecord("Chest_Bench Press", completed) } returns Unit

      // When
      subject.deleteSetRecord("Chest_Bench Press", completed)

      // Then
      coVerify { exerciseDao.deleteSetRecord("Chest_Bench Press", completed) }
    }
  }

  @Nested
  @DisplayName("addCustomExercise")
  inner class AddCustomExercise {
    @Test
    fun `adds an open set at the next step`() = runTest {
      // Given
      coEvery { exerciseDao.getMaxPrimaryStep("2", workoutName) } returns 2
      coEvery { exerciseDao.getExercise("Chest_Push-Up", workoutName) } returns flowOf(null)
      val storedExercise = slot<RoomExercise>()
      val storedSet = slot<RoomExerciseSet>()
      coEvery {
        exerciseDao.storeExerciseAndSet(capture(storedExercise), capture(storedSet))
      } returns Unit

      // When
      subject.addCustomExercise(workoutName, "2", "Chest_Push-Up")

      // Then - the id is stored as-is, whether it's a fresh "{muscleGroup}_{name}" id or an
      // existing catalog exercise's id being reused for shared record history.
      assertThat(storedExercise.captured).isEqualTo(RoomExercise(workout = workoutName, id = "Chest_Push-Up"))
      assertThat(storedSet.captured.day).isEqualTo("2")
      assertThat(storedSet.captured.step).isEqualTo("3")
      assertThat(storedSet.captured.primaryStep).isEqualTo(3)
      assertThat(storedSet.captured.name).isEqualTo("Chest_Push-Up")
      // No set limit yet - targets fill in as the user logs, same convention as challenge sets
      assertThat(storedSet.captured.sets).isEqualTo(-1)
      assertThat(storedSet.captured.reps).isEqualTo(-1)
      assertThat(storedSet.captured.rest).isEqualTo(90)
    }

    @Test
    fun `starts at step 1 for an empty day`() = runTest {
      // Given
      coEvery { exerciseDao.getMaxPrimaryStep("1", workoutName) } returns 0
      coEvery { exerciseDao.getExercise("Chest_Push-Up", workoutName) } returns flowOf(null)
      val storedSet = slot<RoomExerciseSet>()
      coEvery { exerciseDao.storeExerciseAndSet(any(), capture(storedSet)) } returns Unit

      // When
      subject.addCustomExercise(workoutName, "1", "Chest_Push-Up")

      // Then
      assertThat(storedSet.captured.step).isEqualTo("1")
      assertThat(storedSet.captured.primaryStep).isEqualTo(1)
    }

    @Test
    fun `carries the source exercise's description across`() = runTest {
      // Given
      coEvery { exerciseDao.getMaxPrimaryStep("2", workoutName) } returns 0
      val storedExercise = slot<RoomExercise>()
      coEvery { exerciseDao.storeExerciseAndSet(capture(storedExercise), any()) } returns Unit

      // When
      subject.addCustomExercise(workoutName, "2", "Chest_Push-Up", description = "Keep your core tight")

      // Then
      assertThat(storedExercise.captured.description).isEqualTo("Keep your core tight")
    }

    @Test
    fun `falls back to the existing stored description when none is passed`() = runTest {
      // Given - e.g. re-adding an exercise the picker didn't have a cached description for
      coEvery { exerciseDao.getMaxPrimaryStep("2", workoutName) } returns 0
      coEvery { exerciseDao.getExercise("Chest_Push-Up", workoutName) } returns
        flowOf(RoomExercise(workout = workoutName, id = "Chest_Push-Up", description = "Existing description"))
      val storedExercise = slot<RoomExercise>()
      coEvery { exerciseDao.storeExerciseAndSet(capture(storedExercise), any()) } returns Unit

      // When
      subject.addCustomExercise(workoutName, "2", "Chest_Push-Up", description = null)

      // Then - never clobbered by a blank incoming description
      assertThat(storedExercise.captured.description).isEqualTo("Existing description")
    }
  }

  @Nested
  @DisplayName("addAlternateExercise")
  inner class AddAlternateExercise {
    private val baseSet = simpleRoomSet.copy(day = "2", step = "3", primaryStep = 3)

    private fun givenDaySets(vararg sets: RoomExerciseSet): CapturingSlot<RoomExerciseSet> {
      coEvery { exerciseDao.loadDayExerciseSets("2", workoutName) } returns sets.toList()
      coEvery { exerciseDao.getExercise("Chest_Push-Up", workoutName) } returns flowOf(null)
      val storedSet = slot<RoomExerciseSet>()
      coEvery { exerciseDao.storeExerciseAndSet(any(), capture(storedSet)) } returns Unit
      return storedSet
    }

    @Test
    fun `adds the first alternate as suffix a`() = runTest {
      // Given
      val storedSet = givenDaySets(baseSet)

      // When
      subject.addAlternateExercise(workoutName, "2", "3", "Chest_Push-Up")

      // Then
      assertThat(storedSet.captured.step).isEqualTo("3.a")
      assertThat(storedSet.captured.primaryStep).isEqualTo(3)
      assertThat(storedSet.captured.superSetStep).isNull()
      assertThat(storedSet.captured.alternateStep).isEqualTo("a")
      assertThat(storedSet.captured.name).isEqualTo("Chest_Push-Up")
    }

    @Test
    fun `takes the next free suffix after the existing alternates`() = runTest {
      // Given
      val storedSet = givenDaySets(
        baseSet,
        baseSet.copy(step = "3.a", alternateStep = "a"),
        // "3.1" is a different primary step's superset member, not an alternate of "3"
        baseSet.copy(step = "3.1", superSetStep = 1)
      )

      // When
      subject.addAlternateExercise(workoutName, "2", "3", "Chest_Push-Up")

      // Then
      assertThat(storedSet.captured.step).isEqualTo("3.b")
      assertThat(storedSet.captured.alternateStep).isEqualTo("b")
    }

    @Test
    fun `keeps a superset member's position when adding its alternate`() = runTest {
      // Given
      val storedSet = givenDaySets(baseSet.copy(step = "2.3", primaryStep = 2, superSetStep = 3))

      // When
      subject.addAlternateExercise(workoutName, "2", "2.3", "Chest_Push-Up")

      // Then - the alternate has to sort inside the same superset slot as its base
      assertThat(storedSet.captured.step).isEqualTo("2.3.a")
      assertThat(storedSet.captured.primaryStep).isEqualTo(2)
      assertThat(storedSet.captured.superSetStep).isEqualTo(3)
      assertThat(storedSet.captured.alternateStep).isEqualTo("a")
    }

    @Test
    fun `inherits the base set's prescription`() = runTest {
      // Given
      val storedSet = givenDaySets(
        baseSet.copy(sets = 4, reps = 8, rest = 120, repsRange = 2, note = "Base exercise cue")
      )

      // When
      subject.addAlternateExercise(workoutName, "2", "3", "Chest_Push-Up")

      // Then - an alternate substitutes for the same slot, so it starts on the same targets
      assertThat(storedSet.captured.sets).isEqualTo(4)
      assertThat(storedSet.captured.reps).isEqualTo(8)
      assertThat(storedSet.captured.rest).isEqualTo(120)
      assertThat(storedSet.captured.repsRange).isEqualTo(2)
      // ...but not the base's instructions, which describe a different exercise
      assertThat(storedSet.captured.note).isEmpty()
    }

    @Test
    fun `carries the source exercise's description across`() = runTest {
      // Given
      coEvery { exerciseDao.loadDayExerciseSets("2", workoutName) } returns listOf(baseSet)
      val storedExercise = slot<RoomExercise>()
      coEvery { exerciseDao.storeExerciseAndSet(capture(storedExercise), any()) } returns Unit

      // When
      subject.addAlternateExercise(workoutName, "2", "3", "Chest_Push-Up", "Keep your core tight")

      // Then
      assertThat(storedExercise.captured.description).isEqualTo("Keep your core tight")
    }

    @Test
    fun `throws when the base step is missing`() = runTest {
      // Given
      givenDaySets(baseSet.copy(step = "1", primaryStep = 1))

      // When
      val result = runCatching { subject.addAlternateExercise(workoutName, "2", "3", "Chest_Push-Up") }

      // Then
      assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
      coVerify(exactly = 0) { exerciseDao.storeExerciseAndSet(any(), any()) }
    }

    @Test
    fun `throws once every suffix is taken`() = runTest {
      // Given - primaryStep only strips a single letter, so there's no "3.aa"
      givenDaySets(
        baseSet,
        *('a'..'z').map { baseSet.copy(step = "3.$it", alternateStep = it.toString()) }.toTypedArray()
      )

      // When
      val result = runCatching { subject.addAlternateExercise(workoutName, "2", "3", "Chest_Push-Up") }

      // Then
      assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
      coVerify(exactly = 0) { exerciseDao.storeExerciseAndSet(any(), any()) }
    }
  }

  @Nested
  @DisplayName("updateCustomExerciseSet")
  inner class UpdateCustomExerciseSet {
    @Test
    fun `overwrites the existing set's targets, keeping other fields`() = runTest {
      // Given
      coEvery { exerciseDao.loadExerciseSet("2", workoutName, "3") } returns simpleRoomSet.copy(day = "2", step = "3")
      val storedSet = slot<RoomExerciseSet>()
      coEvery { exerciseDao.storeExerciseSet(capture(storedSet)) } returns Unit

      // When
      subject.updateCustomExerciseSet(workoutName, "2", "3", sets = 4, reps = 12, rest = 45, repsRange = 2)

      // Then
      assertThat(storedSet.captured.sets).isEqualTo(4)
      assertThat(storedSet.captured.reps).isEqualTo(12)
      assertThat(storedSet.captured.rest).isEqualTo(45)
      assertThat(storedSet.captured.repsRange).isEqualTo(2)
      assertThat(storedSet.captured.name).isEqualTo(simpleRoomSet.name)
    }

    @Test
    fun `does nothing when the set doesn't exist`() = runTest {
      // Given
      coEvery { exerciseDao.loadExerciseSet("2", workoutName, "3") } returns null

      // When
      subject.updateCustomExerciseSet(workoutName, "2", "3", sets = 4, reps = 12, rest = 45, repsRange = 0)

      // Then
      coVerify(exactly = 0) { exerciseDao.storeExerciseSet(any()) }
    }
  }

  @Nested
  @DisplayName("deleteCustomExerciseSet")
  inner class DeleteCustomExerciseSet {
    private val baseSet = simpleRoomSet.copy(day = "2", step = "3", primaryStep = 3)

    private fun givenRemainingDaySets(
      vararg sets: RoomExerciseSet,
      deletedStep: String = "3"
    ): CapturingSlot<RoomExerciseSet> {
      coEvery { exerciseDao.deleteExerciseSet("2", workoutName, deletedStep) } returns Unit
      coEvery { exerciseDao.loadDayExerciseSets("2", workoutName) } returns sets.toList()
      val moved = slot<RoomExerciseSet>()
      coEvery { exerciseDao.moveExerciseSet(any(), capture(moved)) } returns Unit
      return moved
    }

    @Test
    fun `deletes the set by day, workout, and step`() = runTest {
      // Given
      givenRemainingDaySets()

      // When
      subject.deleteCustomExerciseSet(workoutName, "2", "3")

      // Then
      coVerify { exerciseDao.deleteExerciseSet("2", workoutName, "3") }
    }

    @Test
    fun `promotes the lowest surviving alternate into the deleted base's step`() = runTest {
      // Given - base "3" deleted, "3.a" and "3.b" remain
      val moved = givenRemainingDaySets(
        baseSet.copy(step = "3.a", alternateStep = "a"),
        baseSet.copy(step = "3.b", alternateStep = "b")
      )

      // When
      subject.deleteCustomExerciseSet(workoutName, "2", "3")

      // Then
      assertThat(moved.captured.step).isEqualTo("3")
      assertThat(moved.captured.primaryStep).isEqualTo(3)
      assertThat(moved.captured.superSetStep).isNull()
      assertThat(moved.captured.alternateStep).isNull()
    }

    @Test
    fun `keeps a promoted superset member in its superset slot`() = runTest {
      // Given - base "2.3" deleted, "2.3.a" remains
      val moved = givenRemainingDaySets(
        baseSet.copy(step = "2.3.a", primaryStep = 2, superSetStep = 3, alternateStep = "a"),
        deletedStep = "2.3"
      )

      // When
      subject.deleteCustomExerciseSet(workoutName, "2", "2.3")

      // Then
      assertThat(moved.captured.step).isEqualTo("2.3")
      assertThat(moved.captured.primaryStep).isEqualTo(2)
      assertThat(moved.captured.superSetStep).isEqualTo(3)
      assertThat(moved.captured.alternateStep).isNull()
    }

    @Test
    fun `does not promote when the deleted step was itself an alternate`() = runTest {
      // Given - "3.a" deleted, base "3" and "3.b" remain
      givenRemainingDaySets(
        baseSet, baseSet.copy(step = "3.b", alternateStep = "b"),
        deletedStep = "3.a"
      )

      // When
      subject.deleteCustomExerciseSet(workoutName, "2", "3.a")

      // Then
      coVerify(exactly = 0) { exerciseDao.moveExerciseSet(any(), any()) }
    }

    @Test
    fun `does not promote when the deleted exercise had no alternates`() = runTest {
      // Given
      givenRemainingDaySets()

      // When
      subject.deleteCustomExerciseSet(workoutName, "2", "3")

      // Then
      coVerify(exactly = 0) { exerciseDao.moveExerciseSet(any(), any()) }
    }

    @Test
    fun `an alternate can be added again after the base is promoted away`() = runTest {
      // Given - base "3" deleted, "3.a" survives and is promoted to "3"
      givenRemainingDaySets(baseSet.copy(step = "3.a", alternateStep = "a"))
      subject.deleteCustomExerciseSet(workoutName, "2", "3")

      // When - the promoted set now sits at "3", so the anchor lookup succeeds again
      coEvery { exerciseDao.loadDayExerciseSets("2", workoutName) } returns listOf(
        baseSet.copy(alternateStep = null)
      )
      coEvery { exerciseDao.getExercise("Chest_Push-Up", workoutName) } returns flowOf(null)
      val stored = slot<RoomExerciseSet>()
      coEvery { exerciseDao.storeExerciseAndSet(any(), capture(stored)) } returns Unit

      subject.addAlternateExercise(workoutName, "2", "3", "Chest_Push-Up")

      // Then
      assertThat(stored.captured.step).isEqualTo("3.a")
    }
  }

  @Nested
  @DisplayName("updateCustomExerciseSetNote")
  inner class UpdateCustomExerciseSetNote {
    @Test
    fun `overwrites the existing set's note, keeping other fields`() = runTest {
      // Given
      coEvery { exerciseDao.loadExerciseSet("2", workoutName, "3") } returns simpleRoomSet.copy(day = "2", step = "3")
      val storedSet = slot<RoomExerciseSet>()
      coEvery { exerciseDao.storeExerciseSet(capture(storedSet)) } returns Unit

      // When
      subject.updateCustomExerciseSetNote(workoutName, "2", "3", note = "Keep elbows tucked")

      // Then
      assertThat(storedSet.captured.note).isEqualTo("Keep elbows tucked")
      assertThat(storedSet.captured.reps).isEqualTo(simpleRoomSet.reps)
    }

    @Test
    fun `does nothing when the set doesn't exist`() = runTest {
      // Given
      coEvery { exerciseDao.loadExerciseSet("2", workoutName, "3") } returns null

      // When
      subject.updateCustomExerciseSetNote(workoutName, "2", "3", note = "Keep elbows tucked")

      // Then
      coVerify(exactly = 0) { exerciseDao.storeExerciseSet(any()) }
    }
  }

  @Nested
  @DisplayName("exercisesByMuscle")
  inner class ExercisesByMuscle {
    @Test
    fun `queries every prefix mapped to the muscle group, including Compound, and merges results`() = runTest {
      // Given - "Chest" maps to just itself, but every category also queries the shared
      // Compound bucket (see MuscleGroup.prefixesFor)
      val roomExercise = RoomExercise(workout = workoutName, id = "Chest_Push-Up")
      val compoundExercise = RoomExercise(workout = workoutName, id = "Compound_Burpees")
      coEvery { exerciseDao.getExercisesByMusclePrefix("Chest_") } returns flowOf(listOf(roomExercise))
      coEvery { exerciseDao.getExercisesByMusclePrefix("Compound_") } returns flowOf(listOf(compoundExercise))

      // When / Then
      subject.exercisesByMuscle("Chest").test {
        assertThat(awaitItem()).containsExactly(roomExercise.toDomain(), compoundExercise.toDomain())
        awaitComplete()
      }
    }

    @Test
    fun `queries every stored spelling variant for a multi-prefix category`() = runTest {
      // Given - "Quads" also covers the "Quadricep" spelling some admin programs use
      val quadsExercise = RoomExercise(workout = workoutName, id = "Quads_Barbell Squats")
      coEvery { exerciseDao.getExercisesByMusclePrefix("Quads_") } returns flowOf(listOf(quadsExercise))
      coEvery { exerciseDao.getExercisesByMusclePrefix("Quadricep_") } returns flowOf(emptyList())
      coEvery { exerciseDao.getExercisesByMusclePrefix("Compound_") } returns flowOf(emptyList())

      // When / Then
      subject.exercisesByMuscle("Quads").test {
        assertThat(awaitItem()).containsExactly(quadsExercise.toDomain())
        awaitComplete()
      }
    }
  }

  @Nested
  @DisplayName("loadRemoteExercisesByMuscle")
  inner class LoadRemoteExercisesByMuscle {
    @Test
    fun `fans out to every mapped prefix, caches results, and merges them`() = runTest {
      // Given
      val remoteExercise = Exercise(workoutName, "Chest_Cable Fly")
      val compoundExercise = Exercise(workoutName, "Compound_Burpees")
      coEvery { networkService.getExercisesByMuscle(workoutName, "Chest") } returns listOf(remoteExercise)
      coEvery { networkService.getExercisesByMuscle(workoutName, "Compound") } returns listOf(compoundExercise)
      coEvery { exerciseDao.storeExercises(any()) } returns Unit

      // When
      val result = subject.loadRemoteExercisesByMuscle(workoutName, "Chest")

      // Then
      assertThat(result).containsExactly(remoteExercise, compoundExercise)
      coVerify {
        exerciseDao.storeExercises(
          listOf(RoomExercise.fromDomain(remoteExercise), RoomExercise.fromDomain(compoundExercise))
        )
      }
    }
  }
}