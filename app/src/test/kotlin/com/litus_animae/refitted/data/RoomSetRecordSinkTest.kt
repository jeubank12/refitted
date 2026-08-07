package com.litus_animae.refitted.data

import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.room.ExerciseDao
import com.litus_animae.refitted.room.RefittedRoom
import com.litus_animae.refitted.room.RefittedRoomProvider
import com.litus_animae.refitted.room.entities.RoomSetRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.coroutines.flow.flowOf

@ExperimentalCoroutinesApi
class RoomSetRecordSinkTest {

  private val roomProvider: RefittedRoomProvider = mockk()
  private val roomDatabase: RefittedRoom = mockk()
  private val exerciseDao: ExerciseDao = mockk()

  private lateinit var subject: RoomSetRecordSink

  private val targetExerciseSet = ExerciseSet(
    workout = "TestWorkout",
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
    exercise = flowOf(mockk())
  )

  @BeforeEach
  fun setUp() {
    every { roomProvider.refittedRoom } returns roomDatabase
    every { roomDatabase.getExerciseDao() } returns exerciseDao
    subject = RoomSetRecordSink(roomProvider)
  }

  @Test
  fun `writes each record through to the DAO`() = runTest {
    coEvery { exerciseDao.storeExerciseRecord(any()) } returns Unit
    val watchRecord = SetRecord(weight = 135.0, reps = 8, targetExerciseSet)

    subject.store(listOf(watchRecord))

    coVerify { exerciseDao.storeExerciseRecord(RoomSetRecord.fromDomain(watchRecord)) }
  }

  @Test
  fun `stores multiple records in the same call`() = runTest {
    coEvery { exerciseDao.storeExerciseRecord(any()) } returns Unit
    val first = SetRecord(weight = 135.0, reps = 8, targetExerciseSet)
    val second = SetRecord(weight = 140.0, reps = 6, targetExerciseSet)

    subject.store(listOf(first, second))

    coVerify { exerciseDao.storeExerciseRecord(RoomSetRecord.fromDomain(first)) }
    coVerify { exerciseDao.storeExerciseRecord(RoomSetRecord.fromDomain(second)) }
  }
}
