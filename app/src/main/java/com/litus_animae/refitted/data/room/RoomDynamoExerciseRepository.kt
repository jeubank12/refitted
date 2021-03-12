package com.litus_animae.refitted.data.room

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import arrow.fx.IO
import arrow.fx.extensions.fx
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.dynamo.DynamoExerciseDataService
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.RoomExerciseSet
import com.litus_animae.refitted.models.SetRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.inject.Inject

class RoomDynamoExerciseRepository @Inject constructor(@ApplicationContext context: Context) :
    ExerciseRepository {
    private val applicationContext: WeakReference<Context> =
        WeakReference(context.applicationContext)
    private val roomDb = RoomExerciseDataService.getExerciseRoom(applicationContext)

    private var currentWorkout = MutableLiveData<String>()
    override val workoutRecords = Transformations.switchMap(currentWorkout) {
        roomDb.getExerciseDao().getDayCompletedSets(it)
    }

    data class WorkoutAndDay(val day: String, val workoutId: String)

    private val currentWorkoutDay = MutableLiveData<WorkoutAndDay>()

    override val exercises = MediatorLiveData<List<ExerciseSet>>()
    override val records = Transformations.map(exercises, this::getRecordsForLoadedExercises)

    private val currentStepsSource =
        Transformations.switchMap(currentWorkoutDay) {
            Transformations.map(
                roomDb.getExerciseDao().getSteps(it.day, it.workoutId)
            ) { collection: List<String> ->
                Log.i(TAG, "currentStepsSource: updated to workout ${it.workoutId}, day ${it.day}")
                collection.toSet()
            }
        }

    private val changedStepsSource: LiveData<Set<String>> =
        Transformations.map(currentStepsSource) { stepKeys ->
            Log.i(TAG, "changedStepsSource: received new values for steps")
            if (stepKeys.isEmpty()) {
                Log.i(TAG, "changedStepsSource: no keys loaded yet, waiting...")
                emptySet()
            } else {
                val lastExercises = currentSetsSource.value
                if (lastExercises != null && stepKeys.size == lastExercises.size
                    && doListsFullyIntersect(stepKeys, lastExercises)
                ) {
                    Log.i(
                        TAG,
                        "changedStepsSource: exercise set numbers were updated, but there are no changes"
                    )
                    emptySet()
                } else {
                    if (lastExercises == null) {
                        Log.i(
                            TAG,
                            "changedStepsSource: found exercise set numbers, updating the livedata"
                        )
                    } else {
                        Log.i(
                            TAG,
                            "changedStepsSource: found new exercise set numbers, updating the livedata"
                        )
                    }
                    stepKeys
                }
            }
        }

    private val currentSetsSource = Transformations.switchMap(currentWorkoutDay) {
        Transformations.switchMap(
            changedStepsSource
        ) { steps: Set<String> ->
            Log.i(TAG, "currentSetsSource: steps updated, reloading sets")
            roomDb.getExerciseDao().getExerciseSets(it.day, it.workoutId, *steps.toTypedArray())
        }
    }

    init {
        exercises.addSource(currentSetsSource) { exerciseSets: List<RoomExerciseSet> ->
            updateExercisesWithMinimumChange(
                exerciseSets
            )
        }
    }

    override fun storeSetRecord(record: SetRecord): IO<Unit> {
        return IO.fx {
            continueOn(Dispatchers.IO)
            Log.d(TAG, "storing set record")
            !effect { roomDb.getExerciseDao().storeExerciseRecord(record) }
            Log.d(TAG, "stored set record")
        }
    }

    override fun loadWorkoutRecords(workoutId: String) {
        currentWorkout.value = workoutId
    }

    override fun loadExercises(day: String, workoutId: String): IO<Unit> {
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

    private fun updateExercisesWithMinimumChange(exerciseSets: List<RoomExerciseSet>) {
        Log.i(
            TAG,
            "updateExercisesWithMinimumChange: detected " + exerciseSets.size + " new exercise sets, loading exercise descriptions"
        )

        exercises.value = exerciseSets.sortedWith(compareByStep).map { exerciseSet ->
            ExerciseSet(
                roomExerciseSet = exerciseSet,
                exercise = exercises.value?.find { oldVal -> oldVal.name == exerciseSet.name }?.exercise
                    ?: roomDb.getExerciseDao().getExercise(exerciseSet.name, exerciseSet.workout)
            )
        }
    }

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
