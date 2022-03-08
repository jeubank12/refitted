package com.litus_animae.refitted.data.room

import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.models.DayAndWorkout
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.SetRecord
import com.litus_animae.refitted.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.inject.Inject

@ExperimentalCoroutinesApi
@FlowPreview
class RoomCacheExerciseRepository @Inject constructor(
    private val refittedRoom: RefittedRoom,
    networkService: ExerciseSetNetworkService,
    private val log: LogUtil
) : ExerciseRepository {
    private val mediator = ExerciseSetRemoteMediator(refittedRoom, networkService, log)

    private val currentWorkout = MutableStateFlow("")
    override val workoutRecords = currentWorkout.flatMapLatest {
        refittedRoom.getExerciseDao().getDayCompletedSets(it)
    }

    private val currentWorkoutDay = MutableStateFlow(DayAndWorkout("", ""))

    override val exercises = currentWorkoutDay.flatMapLatest {
        log.d(TAG, "Observed changed currentWorkoutDay to $it")
        mediator.exerciseSets(it)
    }

    override val records = exercises.map(this::getRecordsForLoadedExercises)

    override suspend fun storeSetRecord(record: SetRecord) {
        withContext(Dispatchers.IO) {
            log.d(TAG, "storing set record")
            refittedRoom.getExerciseDao().storeExerciseRecord(record)
            log.d(TAG, "stored set record")
        }
    }

    override fun loadWorkoutRecords(workoutId: String) {
        currentWorkout.value = workoutId
    }

    override suspend fun loadExercises(day: String, workoutId: String) {
        log.i(TAG, "loadExercises: updating to workout $workoutId, day $day")
        currentWorkoutDay.value = DayAndWorkout(day, workoutId)
    }

    private fun getRecordsForLoadedExercises(loadedExercises: List<ExerciseSet>): List<ExerciseRecord> {
        log.i(
            TAG,
            "getRecordsForLoadedExercises: detected " + loadedExercises.size + " new exercises, loading records"
        )
        val tonightMidnight = Date.from(
            LocalDateTime.now().toLocalDate().atStartOfDay().toInstant(ZoneOffset.ofHours(0))
        )
        val recordObjects = loadedExercises.map { e ->
            ExerciseRecord(
                e,
                refittedRoom.getExerciseDao().getLatestSetRecord(e.exerciseName),
                refittedRoom.getExerciseDao().getAllSetRecord(e.exerciseName),
                refittedRoom.getExerciseDao()
                    .getSetRecords(tonightMidnight, e.exerciseName)
            )
        }
        log.i(TAG, "getRecordsForLoadedExercises: records loaded")
        return recordObjects
    }

    companion object {
        private const val TAG = "RoomCacheExerciseRepository"
    }
}
