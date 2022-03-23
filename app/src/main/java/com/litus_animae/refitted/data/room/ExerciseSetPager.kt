package com.litus_animae.refitted.data.room

import androidx.paging.*
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.models.DayAndWorkout
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.RoomExerciseSet
import com.litus_animae.refitted.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
class ExerciseSetPager(
    dayAndWorkout: DayAndWorkout,
    refittedRoom: RefittedRoom,
    private val networkService: ExerciseSetNetworkService,
    private val log: LogUtil
) {
    private val exerciseDao = refittedRoom.getExerciseDao()

    private val remoteMediator = object: RemoteMediator<Int, String>()
    {
        override suspend fun initialize(): InitializeAction {
            val (day, workout) = dayAndWorkout
            return if (exerciseDao.loadSteps(day, workout).isNotEmpty())
                InitializeAction.SKIP_INITIAL_REFRESH
            else InitializeAction.LAUNCH_INITIAL_REFRESH
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
            networkSets
                .onEach {
                    // TODO lift exercise out of a flow
                    val maybeExercise = it.exercise.firstOrNull()
                    log.d(TAG, "Saving ${maybeExercise}, $it")
                    maybeExercise?.let { exercise ->
                        exerciseDao.storeExerciseAndSet(exercise, RoomExerciseSet(it))
                    }
                }
            return MediatorResult.Success(endOfPaginationReached = true)
        }
    }

    val pagingData: Flow<PagingData<ExerciseSet>> = Pager(
        config = PagingConfig(20),
        remoteMediator = remoteMediator
    ) {
        exerciseDao.getStepsPages(dayAndWorkout.day, dayAndWorkout.workoutId)
    }.flow.mapLatest { it.map { step ->
        val set = exerciseDao.loadExerciseSet(dayAndWorkout.day, dayAndWorkout.workoutId, step)!!
        val exercise = exerciseDao.getExercise(set.name, set.workout)
        ExerciseSet(set, exercise.flowOn(Dispatchers.IO))
    } }

    companion object {
        private const val TAG = "ExerciseSetRemoteMediator"
    }
}