package com.litus_animae.refitted.data.room

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.data.models.DayAndWorkout
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.room.RefittedRoomProvider
import com.litus_animae.refitted.room.entities.RoomExerciseSet
import com.litus_animae.refitted.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
class ExerciseSetPager(
  dayAndWorkout: DayAndWorkout,
  roomProvider: RefittedRoomProvider,
  private val networkService: ExerciseSetNetworkService,
  private val log: LogUtil
) {
  private val exerciseDao by lazy { roomProvider.refittedRoom.getExerciseDao() }

  private val remoteMediator = object : RemoteMediator<Int, String>() {
    override suspend fun initialize(): InitializeAction {
      Log.d(TAG, "initializing")
      val (day, workout) = dayAndWorkout
      return withContext(Dispatchers.IO) {
        if (exerciseDao.loadSteps(day, workout).isNotEmpty())
          InitializeAction.SKIP_INITIAL_REFRESH
        else InitializeAction.LAUNCH_INITIAL_REFRESH
      }
    }

    override suspend fun load(
      loadType: LoadType,
      state: PagingState<Int, String>
    ): MediatorResult {
      when (loadType) {
        LoadType.APPEND, LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
        else -> {}
      }
      val networkSets = networkService.getExerciseSets(dayAndWorkout)
      log.d(TAG, "Storing to cache: $networkSets")
      val (exercises, sets) = networkSets.map {
        val roomExerciseSet = RoomExerciseSet.fromDomain(it.set)
        log.d(TAG, "Saving ${it.exercise}, $roomExerciseSet")
        Pair(com.litus_animae.refitted.room.entities.RoomExercise.fromDomain(it.exercise), roomExerciseSet)
      }.unzip()
      exerciseDao.storeExercisesAndSets(dayAndWorkout, exercises, sets)
      return MediatorResult.Success(endOfPaginationReached = true)
    }
  }

  val pagingData: Flow<PagingData<ExerciseSet>> = Pager(
    config = PagingConfig(20),
    remoteMediator = remoteMediator
  ) {
    exerciseDao.getStepsPages(dayAndWorkout.day, dayAndWorkout.workoutId)
  }.flow.mapLatest {
    it.map { step ->
      val roomSet = exerciseDao.loadExerciseSet(dayAndWorkout.day, dayAndWorkout.workoutId, step)!!
      val exercise = exerciseDao.getExercise(roomSet.name, roomSet.workout).map { it?.toDomain() }.flowOn(Dispatchers.IO)
      ExerciseSet(
        workout = roomSet.workout,
        day = roomSet.day,
        step = roomSet.step,
        name = roomSet.name,
        note = roomSet.note,
        reps = roomSet.reps,
        sets = roomSet.sets,
        isToFailure = roomSet.isToFailure,
        rest = roomSet.rest,
        repsUnit = roomSet.repsUnit,
        repsRange = roomSet.repsRange,
        timeLimit = roomSet.timeLimit,
        timeLimitUnit = roomSet.timeLimitUnit,
        repsSequence = roomSet.repsSequence,
        exercise = exercise
      )
    }
  }.flowOn(Dispatchers.IO)

  companion object {
    private const val TAG = "ExerciseSetRemoteMediator"
  }
}