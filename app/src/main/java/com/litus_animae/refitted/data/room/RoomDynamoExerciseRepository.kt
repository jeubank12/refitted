package com.litus_animae.refitted.data.room

import android.content.Context
import android.util.Log
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.dynamo.DynamoExerciseDataService
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.RoomExerciseSet
import com.litus_animae.refitted.models.SetRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.inject.Inject

@FlowPreview
class RoomDynamoExerciseRepository @Inject constructor(@ApplicationContext context: Context) :
    ExerciseRepository {
    private val applicationContext: WeakReference<Context> =
        WeakReference(context.applicationContext)
    private val roomDb = RoomExerciseDataService.getExerciseRoom(applicationContext)

    private var currentWorkout = MutableStateFlow("")
    override val workoutRecords = currentWorkout.flatMapConcat {
        roomDb.getExerciseDao().getDayCompletedSets(it)
    }

    data class WorkoutAndDay(val day: String, val workoutId: String)

    private val currentWorkoutDay = MutableStateFlow(WorkoutAndDay("", ""))

    private val currentStepsSource =
        currentWorkoutDay.flatMapConcat {
            roomDb.getExerciseDao().getSteps(it.day, it.workoutId).map { collection: List<String> ->
                Log.i(TAG, "currentStepsSource: updated to workout ${it.workoutId}, day ${it.day}")
                collection.toSet()
            }
        }

    private val currentSetsSource =
        currentWorkoutDay.combine(currentStepsSource) { workoutDay, steps ->
            Log.i(TAG, "currentSetsSource: steps updated, reloading sets")
            roomDb.getExerciseDao()
                .getExerciseSets(workoutDay.day, workoutDay.workoutId, *steps.toTypedArray())
        }.flattenConcat()

    override val exercises = currentSetsSource.map {
        it.sortedWith(compareByStep).map { exerciseSet ->
            ExerciseSet(
                roomExerciseSet = exerciseSet,
                exercise = roomDb.getExerciseDao()
                    .getExercise(exerciseSet.name, exerciseSet.workout)
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
        val dynamoService = DynamoExerciseDataService(applicationContext.get()!!, roomDb)
        return dynamoService.execute(day, workoutId)
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
                roomDb.getExerciseDao().getAllSetRecord(e.exerciseName),
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
