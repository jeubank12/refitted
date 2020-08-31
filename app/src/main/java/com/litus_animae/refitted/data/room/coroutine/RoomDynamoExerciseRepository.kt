package com.litus_animae.refitted.data.room.coroutine

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.dynamo.coroutine.DynamoExerciseDataService
import com.litus_animae.refitted.data.room.ExerciseRoom
import com.litus_animae.refitted.data.room.asynctask.RoomDynamoExerciseRepository
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.RoomExerciseSet
import com.litus_animae.refitted.models.SetRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.inject.Inject

class RoomDynamoExerciseRepository @Inject constructor(@ApplicationContext context: Context) : ExerciseRepository {
    private val applicationContext: WeakReference<Context> = WeakReference(context.applicationContext)
    override val exercises = MediatorLiveData<List<ExerciseSet>>()
    override val records = Transformations.map(exercises, this::getRecordsForLoadedExercises)
    private val changedStepsSource = MediatorLiveData<Set<String>>()
    private var currentStepsSource: LiveData<Set<String>>? = null
    private var currentSetsSource: LiveData<List<RoomExerciseSet>>? = null
    val roomDb = RoomExerciseDataService.getExerciseRoom(applicationContext)

    override fun shutdown() {
        // TODO("Not yet implemented")
    }

    override fun storeSetRecord(record: SetRecord) {
        TODO("Not yet implemented")
    }

    override fun loadExercises(day: String, workoutId: String) {
        if (currentSetsSource != null) {
            Log.d(TAG, "loadExercises: removing previously loaded exercises")
            exercises.removeSource(currentSetsSource!!)
        }
        if (currentStepsSource != null) {
            Log.d(TAG, "loadExercises: removing previously loaded steps")
            changedStepsSource.removeSource(currentStepsSource!!)
        }

        Log.i(TAG, "loadExercises: setting up stepsSource")
        currentStepsSource = getStepsForDayAndWorkout(day, workoutId)

        Log.i(TAG, "loadExercises: setting up change detector for steps")
        changedStepsSource.addSource(currentStepsSource!!) { stepKeys: Set<String> -> this.updateSetsIfChanged(stepKeys) }

        Log.i(TAG, "loadExercises: setting up sets loader based on steps changes")
        currentSetsSource = getExercisesFromSteps(day, workoutId)

        Log.i(TAG, "loadExercises: setting up transformation to load exercise descriptions")
        exercises.addSource(currentSetsSource!!) { exerciseSets: List<RoomExerciseSet> -> updateExercisesWithMinimumChange(exerciseSets) }
    }

    private fun getStepsForDayAndWorkout(day: String, workoutId: String): LiveData<Set<String>> {
        Log.i(TAG, "getStepsForDayAndWorkout: submitting dynamo query for workout $workoutId, day $day")
        val dynamoService = DynamoExerciseDataService(applicationContext.get()!!, roomDb)
        dynamoService.execute(day, workoutId).unsafeRunAsync { res -> res.mapLeft { ex -> Log.wtf(TAG, ex) } }
        Log.i(TAG, "getStepsForDayAndWorkout: returning query for workout steps")
        return Transformations.map(
                roomDb.getExerciseDao().getSteps(day, workoutId)) { collection: List<String> -> collection.toSet() }
    }

    private fun updateSetsIfChanged(stepKeys: Set<String>) {
        Log.i(TAG, "updateSetsIfChanged: received new values for steps")
        if (stepKeys.isEmpty()) {
            Log.i(TAG, "updateSetsIfChanged: no keys loaded yet, waiting...")
            return
        }
        val lastExercises = currentSetsSource!!.value
        if (lastExercises != null && stepKeys.size == lastExercises.size) {
            if (doListsFullyIntersect(stepKeys, lastExercises)) {
                Log.i(TAG, "updateSetsIfChanged: exercise set numbers were updated, but there are no changes")
                return
            }
        }
        if (lastExercises == null) {
            Log.i(TAG, "updateSetsIfChanged: found exercise set numbers, updating the livedata")
        } else {
            Log.i(TAG, "updateSetsIfChanged: found new exercise set numbers, updating the livedata")
        }
        changedStepsSource.value = stepKeys
    }

    private fun doListsFullyIntersect(stepKeys: Set<String>, lastExercises: List<RoomExerciseSet>): Boolean {
        val pairs = stepKeys.sorted().zip(lastExercises.sortedWith(compareByStep))
        return !pairs.any { pair -> pair.first != pair.second.step }
    }

    private val compareByStep = Comparator.comparing(RoomExerciseSet::step)

    private fun getExercisesFromSteps(day: String, workoutId: String): LiveData<List<RoomExerciseSet>> {
        return Transformations.switchMap(changedStepsSource
        ) { steps: Set<String> ->
            Log.i(TAG, "getExercisesFromSteps: steps updated, reloading sets")
            roomDb.getExerciseDao().getExerciseSets(day, workoutId, *steps.toTypedArray())
        }
    }

    private fun updateExercisesWithMinimumChange(exerciseSets: List<RoomExerciseSet>) {
        Log.i(TAG, "updateExercisesWithMinimumChange: detected " + exerciseSets.size + " new exercise sets, loading exercise descriptions")

        exercises.value = exerciseSets.sortedWith(compareByStep).map { exerciseSet ->
            ExerciseSet(
                    roomExerciseSet = exerciseSet,
                    exercise = exercises.value?.find { oldVal -> oldVal.name == exerciseSet.name }?.exercise
                            ?: roomDb.getExerciseDao().getExercise(exerciseSet.name, exerciseSet.workout))
        }
    }

    private fun getRecordsForLoadedExercises(loadedExercises: List<ExerciseSet>): List<ExerciseRecord> {
                Log.i(TAG, "getRecordsForLoadedExercises: detected " + loadedExercises.size + " new exercises, loading records")
                val tonightMidnight = Date.from(LocalDateTime.now().toLocalDate().atStartOfDay().toInstant(ZoneOffset.ofHours(0)))
                val recordObjects = loadedExercises.map { e ->
                    ExerciseRecord(e,
                            roomDb.getExerciseDao().getLatestSetRecord(e.exerciseName),
                            roomDb.getExerciseDao().getAllSetRecord(e.exerciseName),
                            roomDb.getExerciseDao()
                                    .getSetRecords(tonightMidnight, e.exerciseName))
                }
                Log.i(TAG, "getRecordsForLoadedExercises: records loaded")
               return recordObjects
    }

    companion object {
        private const val TAG = "CoroutinesRoomDynamoExerciseRepository"
    }
}
