package com.litus_animae.refitted.data.room

import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.models.DayAndWorkout
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.RoomExerciseSet
import com.litus_animae.refitted.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseSetRemoteMediator(
    refittedRoom: RefittedRoom,
    private val networkService: ExerciseSetNetworkService,
    private val log: LogUtil
) {
    private val exerciseDao = refittedRoom.getExerciseDao()

    private val compareByStep = Comparator.comparing(RoomExerciseSet::step)

    fun exerciseSets(dayAndWorkout: DayAndWorkout): Flow<List<ExerciseSet>> {
        return flow {
            val (workoutDay, workoutId) = dayAndWorkout
            log.i(TAG, "Checking cache for $dayAndWorkout")
            val cachedSteps = exerciseDao.loadSteps(workoutDay, workoutId)
            if (cachedSteps.isEmpty()) {
                log.i(TAG, "Did not find cache for $dayAndWorkout, loading from network service")
                val networkSets = networkService.getExerciseSets(dayAndWorkout)
                log.d(TAG, "Emitting network results then storing to cache: $networkSets")
                emit(networkSets)
                log.d(TAG, "Storing to cache: $networkSets")
                networkSets.map {
                    val maybeExercise = it.exercise.firstOrNull()
                    log.d(TAG, "Saving ${maybeExercise}, $it")
                    maybeExercise?.let { exercise ->
                        exerciseDao.storeExerciseAndSet(exercise, RoomExerciseSet(it))
                    }
                }
                log.d(TAG, "Transitioning to live query")
            } else {
                log.i(TAG, "Found cache for $dayAndWorkout")
            }
            val liveSteps = exerciseDao.getSteps(workoutDay, workoutId)
            val liveSets = liveSteps
                .flatMapLatest {
                    log.d(TAG, "Steps updated, reloading exercise sets")
                    exerciseDao.getExerciseSets(workoutDay, workoutId, *it.toTypedArray())
                }.map {
                    log.d(TAG, "Sets updated, converting to model")
                    it.sortedWith(compareByStep).map { set ->
                        log.d(TAG, "Loading ${set.name} ${set.workout}")
                        val exercise = exerciseDao.getExercise(set.name, set.workout)
                        ExerciseSet(set, exercise.flowOn(Dispatchers.IO))
                    }
                }
            emitAll(liveSets)
        }.flowOn(Dispatchers.IO)
    }

    companion object {
        private const val TAG = "ExerciseSetRemoteMediator"
    }
}