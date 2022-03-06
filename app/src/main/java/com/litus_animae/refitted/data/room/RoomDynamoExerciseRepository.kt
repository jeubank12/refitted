package com.litus_animae.refitted.data.room

import android.content.Context
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.dynamo.DynamoExerciseDataService
import com.litus_animae.refitted.models.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.inject.Inject

@ExperimentalCoroutinesApi
@FlowPreview
class RoomDynamoExerciseRepository @Inject constructor(@ApplicationContext context: Context) :
    ExerciseRepository {
    private val applicationContext = context.applicationContext
    private val roomDb = RoomRefittedDataService.getRefittedRoom(applicationContext)

    private val currentWorkout = MutableStateFlow("")
    override val workoutRecords = currentWorkout.flatMapLatest {
        roomDb.getExerciseDao().getDayCompletedSets(it)
    }

    data class WorkoutAndDay(val day: String, val workoutId: String)

    private val currentWorkoutDay = MutableStateFlow(WorkoutAndDay("", ""))

    private val currentStepsSource =
        currentWorkoutDay.flatMapLatest {
            Log.i(TAG, "currentStepsSource: updated to workout ${it.workoutId}, day ${it.day}")
            roomDb.getExerciseDao().getSteps(it.day, it.workoutId)
                .distinctUntilChanged()
                .map { collection: List<String> ->
                    Log.i(
                        TAG,
                        "currentStepsSource: detected update in steps on workout ${it.workoutId}, day ${it.day}: $collection"
                    )
                    collection.toSet()
                }.distinctUntilChanged()
                .onEach { _ ->
                    Log.i(
                        TAG,
                        "currentStepsSource: propagating change in steps on workout ${it.workoutId}, day ${it.day}"
                    )
                }
        }

    private val currentSetsSource =
        currentWorkoutDay.combine(currentStepsSource) { workoutDay, steps ->
            Log.i(TAG, "currentSetsSource: steps updated, reloading sets")
            roomDb.getExerciseDao()
                .getExerciseSets(workoutDay.day, workoutDay.workoutId, *steps.toTypedArray())
                .distinctUntilChanged()
                .onEach {
                    Log.i(TAG, "currentSetsSource: steps updated, reloaded sets")
                }
        }.flatMapLatest { it }

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
            Log.d(TAG, "storing set record")
            roomDb.getExerciseDao().storeExerciseRecord(record)
            Log.d(TAG, "stored set record")
        }
    }

    override fun loadWorkoutRecords(workoutId: String) {
        currentWorkout.value = workoutId
    }

    override suspend fun loadExercises(day: String, workoutId: String) {
        Log.i(TAG, "loadExercises: updating to workout $workoutId, day $day")
        currentWorkoutDay.value = WorkoutAndDay(day, workoutId)

        Log.i(TAG, "loadExercises: submitting dynamo query for workout $workoutId, day $day")
        val dynamoService = DynamoExerciseDataService(applicationContext, roomDb)
        dynamoService.execute(day, workoutId)
    }

    private fun doListsFullyIntersect(
        stepKeys: Set<String>,
        lastExercises: List<RoomExerciseSet>
    ): Boolean {
        val pairs = stepKeys.sorted().zip(lastExercises.sortedWith(compareByStep))
        return !pairs.any { pair -> pair.first != pair.second.step }
    }

    private val compareByStep = Comparator.comparing(RoomExerciseSet::step)

    private fun getRecordsForLoadedExercises(loadedExercises: List<ExerciseSet>): List<ExerciseRecord> {
        Log.i(
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
        Log.i(TAG, "getRecordsForLoadedExercises: records loaded")
        return recordObjects
    }

    companion object {
        private const val TAG = "CoroutinesRoomDynamoExerciseRepository"
    }
}
