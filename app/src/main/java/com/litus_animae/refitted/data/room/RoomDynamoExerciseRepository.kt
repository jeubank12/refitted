package com.litus_animae.refitted.data.room

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.dynamo.DynamoExerciseDataService
import com.litus_animae.refitted.models.*
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.inject.Inject

@ExperimentalCoroutinesApi
@FlowPreview
class RoomDynamoExerciseRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val log: LogUtil
) :
    ExerciseRepository {
    private val applicationContext = context.applicationContext
    private val roomDb = RoomRefittedDataService.getRefittedRoom(applicationContext)

    private val currentWorkout = MutableStateFlow("")
    override val workoutRecords = currentWorkout.flatMapLatest {
        roomDb.getExerciseDao().getDayCompletedSets(it)
    }

    private val currentWorkoutDay = MutableStateFlow(DayAndWorkout("", ""))

    private val currentStepsSource =
        currentWorkoutDay.flatMapLatest {
            log.i(TAG, "currentStepsSource: updated to workout ${it.workoutId}, day ${it.day}")
            roomDb.getExerciseDao().getSteps(it.day, it.workoutId)
                .distinctUntilChanged()
                .map { collection: List<String> ->
                    log.i(
                        TAG,
                        "currentStepsSource: detected update in steps on workout ${it.workoutId}, day ${it.day}: $collection"
                    )
                    collection.toSet()
                }.distinctUntilChanged()
                .onEach { _ ->
                    log.i(
                        TAG,
                        "currentStepsSource: propagating change in steps on workout ${it.workoutId}, day ${it.day}"
                    )
                }
        }

    private val currentSetsSource =
        currentWorkoutDay.combine(currentStepsSource) { workoutDay, steps ->
            log.i(TAG, "currentSetsSource: steps updated, reloading sets")
            roomDb.getExerciseDao()
                .getExerciseSets(workoutDay.day, workoutDay.workoutId, *steps.toTypedArray())
                .distinctUntilChanged()
                .onEach {
                    log.i(TAG, "currentSetsSource: steps updated, reloaded sets")
                }
        }.flatMapLatest { it }

    override val exercisesAreLoading: StateFlow<Boolean>
        get() = MutableStateFlow(true)

    override val exercises = currentSetsSource.mapLatest {
        it.sortedWith(compareByStep).map { exerciseSet ->
            ExerciseSet(
                roomExerciseSet = exerciseSet,
                exercise = roomDb.getExerciseDao()
                    .getExercise(exerciseSet.name, exerciseSet.workout)
                    .stateIn(CoroutineScope(Dispatchers.IO))
            )
        }
    }
    override val records = exercises.map(this::getRecordsForLoadedExercises)

    override suspend fun storeSetRecord(record: SetRecord) {
        withContext(Dispatchers.IO) {
            log.d(TAG, "storing set record")
            roomDb.getExerciseDao().storeExerciseRecord(record)
            log.d(TAG, "stored set record")
        }
    }

    override fun loadWorkoutRecords(workoutId: String) {
        currentWorkout.value = workoutId
    }

    override suspend fun loadExercises(day: String, workoutId: String) {
        log.i(TAG, "loadExercises: updating to workout $workoutId, day $day")
        currentWorkoutDay.value = DayAndWorkout(day, workoutId)

        log.i(TAG, "loadExercises: submitting dynamo query for workout $workoutId, day $day")
        val dynamoService = DynamoExerciseDataService(applicationContext, roomDb)
        dynamoService.execute(day, workoutId)
    }

    private val compareByStep = Comparator.comparing(RoomExerciseSet::step)

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
                roomDb.getExerciseDao().getLatestSetRecord(e.exerciseName),
                Pager(config = PagingConfig(pageSize = 20)) {
                    roomDb.getExerciseDao().getAllSetRecord(e.exerciseName)
                }.flow,
                roomDb.getExerciseDao()
                    .getSetRecords(tonightMidnight, e.exerciseName)
            )
        }
        log.i(TAG, "getRecordsForLoadedExercises: records loaded")
        return recordObjects
    }

    companion object {
        private const val TAG = "RoomDynamoExerciseRepository"
    }
}
