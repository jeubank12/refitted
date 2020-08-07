package com.litus_animae.refitted.models

import android.os.CountDownTimer
import android.view.View
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import com.litus_animae.refitted.R
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.ParameterizedResource
import com.litus_animae.refitted.util.ParameterizedStringArrayResource
import com.litus_animae.refitted.util.ParameterizedStringResource
import java.util.*
import kotlin.math.roundToInt

class ExerciseViewModel @ViewModelInject constructor(private val exerciseRepo: ExerciseRepository, private val log: LogUtil) : ViewModel() {
    private var timer: CountDownTimer? = null

    private val exerciseSets = MediatorLiveData<List<ExerciseSet>>()
    val exercise: LiveData<ExerciseSet> = Transformations.switchMap(exerciseSets
    ) {
        Transformations.switchMap(exerciseRecords
        ) { Transformations.map(exerciseIndex) { index: Int -> updateVisibleExercise(index) } }
    }
    var targetExerciseReps: LiveData<ParameterizedResource> = Transformations.map(exercise) { (_, _, _, _, reps, _, isToFailure, _, repsUnit, repsRange) ->
        if (reps < 0) {
            ParameterizedStringResource(R.string.to_failure)
        } else {
            val resource = if (repsRange > 0) R.array.exercise_reps_range else R.array.exercise_reps
            var index = 0
            if (repsUnit.isNotEmpty()) {
                index += 2
            }
            if (isToFailure) {
                index += 1
            }
            ParameterizedStringArrayResource(resource, index, arrayOf(reps,
                    reps + repsRange,
                    repsUnit
            ))
        }
    }
    private val _isLoadingBool: MutableLiveData<Boolean> = MutableLiveData(true)
    val isLoadingBool: LiveData<Boolean> = _isLoadingBool
    val isLoading: LiveData<Int> = Transformations.map(_isLoadingBool) { isLoad: Boolean -> if (isLoad) View.VISIBLE else View.GONE }
    private val hasLeftBool: MutableLiveData<Boolean> = MutableLiveData(false)
    val hasLeft: LiveData<Int> = Transformations.map(hasLeftBool) { enable: Boolean -> if (enable) View.VISIBLE else View.GONE }
    private val hasRightBool: MutableLiveData<Boolean> = MutableLiveData(true)
    val hasRight: LiveData<Int> = Transformations.map(hasRightBool) { enable: Boolean -> if (enable) View.VISIBLE else View.GONE }
    private val exerciseRecords: LiveData<List<ExerciseRecord>> = exerciseRepo.records
    private val exerciseIndex = MutableLiveData(0)
    val weightDisplayValue = MediatorLiveData<String>()
    val repsDisplayValue = MediatorLiveData<String>()
    private val timerMutableLiveData = MutableLiveData<CountDownTimer?>(null)
    val currentRecord = Transformations.switchMap(exerciseIndex) { index: Int ->
        Transformations.map(exerciseRecords) { records: List<ExerciseRecord> ->
            records.getOrNull(index)
        }
    }

    // TODO make disabled for a second after click
    val completeSetButtonEnabled = Transformations.switchMap(currentRecord) { record: ExerciseRecord? ->
        if (record == null) {
            MutableLiveData<Boolean>(false)
        } else {
            Transformations.switchMap(exercise) { (_, _, _, _, _, sets) ->
                Transformations.switchMap(record.setsCount) { setsCompleted: Int ->
                    Transformations.map(timerMutableLiveData) { timer: CountDownTimer? ->
                        timer == null || setsCompleted < sets
                    }
                }
            }
        }
    }
    private val restRemaining = MutableLiveData<Double>(0.0)
    private val _restMax = MutableLiveData(0)
    val restMax: LiveData<Int> = _restMax
    val restProgress = Transformations.switchMap(_restMax) { max: Int -> Transformations.map(restRemaining) { rest: Double -> (max - rest * 1000).toInt() } }

    // TODO new text
    val restValue = Transformations.map<Double, ParameterizedResource>(restRemaining) { rest: Double ->
        ParameterizedStringResource(R.string.seconds_rest_phrase, arrayOf(rest))
    }

    // FIXME optimize the layers of transformations
    var completeSetMessage: LiveData<ParameterizedResource> = Transformations.switchMap(currentRecord) { record: ExerciseRecord? ->
        if (record == null) {
            log.w(TAG, "completeSetMessage: record was null")
            MutableLiveData<ParameterizedResource>(ParameterizedStringResource(R.string.complete_set))
        } else {
            Transformations.switchMap(timerMutableLiveData) { timer: CountDownTimer? ->
                if (timer == null) {
                    Transformations.switchMap(record.setsCount) { completeSetsCount: Int ->
                        Transformations.map<ExerciseSet, ParameterizedResource>(exercise) { exercise: ExerciseSet ->
                            _restMax.value = exercise.rest * 1000
                            restRemaining.value = exercise.rest.toDouble()
                            when {
                                completeSetsCount == exercise.sets -> {
                                    ParameterizedStringResource(R.string.complete_exercise)
                                }
                                // TODO if time unit, display "Start Circuit"
                                //                            if (exercise.getRepsUnit() != null && (exercise.getRepsUnit().equalsIgnoreCase("minutes") ||
                                //                                    exercise.getRepsUnit().equalsIgnoreCase("seconds"))) {
                                //                                return "TODO Start Exercise";
                                //                            }
                                exercise.step.contains(".1") -> {
                                    // TODO determine if in sync with part 2
                                    ParameterizedStringResource(R.string.complete_superset_part_1, arrayOf( // using the LiveData here because the value may have changed
                                            completeSetsCount + 1,
                                            exercise.sets
                                    ))
                                }
                                else -> {
                                    ParameterizedStringResource(R.string.complete_set_of_workout, arrayOf(
                                            completeSetsCount + 1,
                                            exercise.sets
                                    ))
                                }
                            }
                        }
                    }
                } else {
                    Transformations.map<ExerciseSet, ParameterizedResource>(exercise) { ParameterizedStringResource(R.string.cancel_rest) }
                }
            }
        }
    }

    // endregion
    private val isBarbellExercise: LiveData<Boolean> = Transformations.map(exercise) { targetSet: ExerciseSet? ->
        when {
            targetSet == null -> {
                false
            }
            targetSet.repsUnit.equals("minutes", ignoreCase = true) || targetSet.repsUnit.equals("seconds", ignoreCase = true) -> {
                false
            }
            targetSet.exerciseName.toLowerCase().contains("db") || targetSet.exerciseName.toLowerCase().contains("dumbbell") -> {
                false
            }
            targetSet.exerciseName.toLowerCase().contains("bb") || targetSet.exerciseName.toLowerCase().contains("barbell") || targetSet.exerciseName.toLowerCase().contains("press") -> {
                true
            }
            targetSet.note.toLowerCase().contains("db") || targetSet.note.toLowerCase().contains("dumbbell") -> {
                false
            }
            else -> {
                targetSet.note.toLowerCase().contains("bb") ||
                        targetSet.note.toLowerCase().contains("barbell") ||
                        targetSet.note.toLowerCase().contains("press")
            }
        }
    }
    val showAsDouble = MediatorLiveData<Boolean>()

    private val weightSeedValue = Transformations.switchMap(currentRecord) { record: ExerciseRecord? ->
        if (record == null) {
            MutableLiveData<String>(formatWeightDisplay(defaultDbWeight))
        } else {
            Transformations.map(record.latestSet) { latestSet: SetRecord? ->
                if (latestSet != null) {
                    formatWeightDisplay(latestSet.weight)
                } else {
                    formatWeightDisplay(determineSetDefaultWeight(record.targetSet))
                }
            }
        }
    }
    private val repsSeedValue = Transformations.switchMap(currentRecord) { record: ExerciseRecord? ->
        if (record == null) {
            MutableLiveData<String>(formatRepsDisplay(0))
        } else {
            Transformations.switchMap(record.setsCount) { count: Int ->
                if (count > 0) {
                    Transformations.map(record.getSet(-1)
                    ) { latestSet: SetRecord? -> formatRepsDisplay(latestSet!!.reps) }
                } else {
                    // TODO target set should be a livedata
                    MutableLiveData<String>(
                            if (record.targetSet.repsRange > 0) {
                                formatRepsDisplay(
                                        record.targetSet.reps +
                                                record.targetSet.repsRange)
                            } else {
                                // TODO if timed, default to last record reps if exists
                                formatRepsDisplay(record.targetSet.reps)
                            })
                }
            }
        }
    }

    private fun determineSetDefaultWeight(targetSet: ExerciseSet): Double =
            when {
                targetSet.repsUnit.equals("minutes", ignoreCase = true) || targetSet.repsUnit.equals("seconds", ignoreCase = true) -> {
                    defaultBodyweight
                }
                targetSet.exerciseName.toLowerCase().contains("db") || targetSet.exerciseName.toLowerCase().contains("dumbbell") -> {
                    defaultDbWeight
                }
                targetSet.exerciseName.toLowerCase().contains("bb") || targetSet.exerciseName.toLowerCase().contains("barbell") || targetSet.exerciseName.toLowerCase().contains("press") -> {
                    defaultBbWeight
                }
                targetSet.note.toLowerCase().contains("db") || targetSet.note.toLowerCase().contains("dumbbell") -> {
                    defaultDbWeight
                }
                targetSet.note.toLowerCase().contains("bb") || targetSet.note.toLowerCase().contains("barbell") || targetSet.note.toLowerCase().contains("press") -> {
                    defaultBbWeight
                }
                else -> defaultBodyweight
            }


    override fun onCleared() {
        super.onCleared()
        exerciseRepo.shutdown()
    }

    fun loadExercises(day: String?, workoutId: String?) {
        _isLoadingBool.value = true
//        startLoad = Instant.now()
        exerciseRepo.loadExercises(day, workoutId)
    }

    fun updateWeightDisplay(change: Double) {
        // leaving this as a warning as I don't know when this would be null
        val value = weightDisplayValue.value!!.toDouble() + change
        if (value < 0) {
            setWeightDisplay(0.0)
        } else {
            setWeightDisplay(value)
        }
    }

    private fun setWeightDisplay(value: Double) {
        weightDisplayValue.value = formatWeightDisplay(value)
    }

    fun updateRepsDisplay(increase: Boolean) {
        val value = repsDisplayValue.value!!.toInt()
        when {
            increase -> {
                setRepsDisplay(value + 1)
            }
            value > 0 -> {
                setRepsDisplay(value - 1)
            }
            else -> {
                setRepsDisplay(0)
            }
        }
    }

    private fun setRepsDisplay(value: Int) {
        repsDisplayValue.value = formatRepsDisplay(value)
    }

    private fun checkForAlternateExerciseSet(index: Int, e: ExerciseSet): Boolean {
        var resultantExercise: ExerciseSet = e
        var result = false
        if (!resultantExercise.hasAlternate() && resultantExercise.step.endsWith(".a")) {
            log.d(TAG, "checkForAlternateExerciseSet: setting up new alternate for .a")
            resultantExercise.alternate = exerciseSets.value!![index + 1]
            resultantExercise.alternate!!.isActive = false
            resultantExercise.alternate!!.alternate = resultantExercise
            result = true
        } else if (!resultantExercise.isActive && resultantExercise.step.endsWith(".a")) {
            log.d(TAG, "checkForAlternateExerciseSet: navigated to .a, but .b is active")
            exerciseIndex.value = index + 1
            resultantExercise = resultantExercise.alternate ?: resultantExercise
        } else if (!resultantExercise.isActive && resultantExercise.step.endsWith(".b")) {
            log.d(TAG, "checkForAlternateExerciseSet: navigated to .b, but .a is active")
            exerciseIndex.value = index - 1
            resultantExercise = resultantExercise.alternate ?: resultantExercise
        }
        resultantExercise.isActive = true
        return result
    }

    private fun updateVisibleExercise(index: Int): ExerciseSet {
        val copyExerciseSets = exerciseSets.value
        if (copyExerciseSets == null || copyExerciseSets.isEmpty()) {
            log.d(TAG, "updateVisibleExercise: exerciseSets is not yet set, returning default")
            return ExerciseSet(MutableExerciseSet())
        }
        // TODO might be able to remove this since the method is only called by livedata transformation
        if (_isLoadingBool.value!!) {
            _isLoadingBool.value = false
        }
        log.d(TAG, "updateVisibleExercise: updating to index $index")
        var e = copyExerciseSets[index]
        var wasChanged = false
        if (e.hasAlternate() || e.step.endsWith(".a")) {
            log.d(TAG, "updateVisibleExercise: checking for alternate...")
            wasChanged = checkForAlternateExerciseSet(index, e)
            e = copyExerciseSets[exerciseIndex.value!!]
            //switchToAlternateButton.setVisible(true);
            hasLeftBool.value = index > if (e.step.endsWith(".b")) 1 else 0
            hasRightBool.setValue(index < copyExerciseSets.size -
                    if (e.step.endsWith(".a")) 2 else 1)
        } else {
            //switchToAlternateButton.setVisible(false);
            hasLeftBool.value = index > 0
            hasRightBool.setValue(index < copyExerciseSets.size - 1)
        }

        //setTimerText(e);
        if (wasChanged) {
            exerciseSets.value = copyExerciseSets
        }
        return e
    }

    fun swapToAlternate() {
        val copyExerciseSet = exerciseSets.value!!
        // leaving this as a warning as I don't know when this would be null
        copyExerciseSet[exerciseIndex.value!!].isActive = false
        exerciseSets.value = copyExerciseSet
    }

    fun navigateLeft() {
        // leaving this as a warning as I don't know when this would be null
        val index = exerciseIndex.value!!
        if (index < 1) {
            log.e(TAG, "handleNavigateLeft: already furthest left")
            exerciseIndex.setValue(0)
        } else {
            val e = exerciseSets.value!![index]
            if (e.step.endsWith(".b")) {
                // TODO write tests for this
                // if the first step, then there will be a '.a'
                if (index != 1) {
                    exerciseIndex.value = index - 2
                }
            } else {
                exerciseIndex.setValue(index - 1)
            }
        }
    }

    fun navigateRight() {
        // leaving this as a warning as I don't know when this would be null
        val index = exerciseIndex.value!!
        val copyExerciseSets = exerciseSets.value!!
        if (index >= copyExerciseSets.size - 1) {
            log.e(TAG, "handleNavigateLeft: already furthest right")
            exerciseIndex.setValue(copyExerciseSets.size - 1)
        } else {
            val e = copyExerciseSets[index]
            if (e.step.endsWith(".a")) {
                // if the last step, then there will be a '.b'
                if (index != copyExerciseSets.size - 2) {
                    exerciseIndex.value = (index + 2).coerceAtMost(copyExerciseSets.size - 1)
                }
            } else {
                exerciseIndex.setValue(index + 1)
            }
        }
    }

    fun completeSet(weight: String, reps: String) {
        // leaving this as a warning as I don't know when this would be null
        val index = exerciseIndex.value!!
        val exerciseSet = exerciseSets.value!![index]
        // if there is a timer, then this is a cancel button
        // TODO if this is a timed-exercise, detect whether we are in rest or execute
        if (timer != null) {
            timer!!.cancel()
            timer = null
            // TODO if this is a timed-exercise, use unit instead of restmax
            restRemaining.value = _restMax.value?.toDouble()
            timerMutableLiveData.value = null
            //setTimerText(exerciseSet);
            return
        }

        // FIXME this won't be correct if not observed, but it would only be necessary if not observed either, double negative
        if (!completeSetButtonEnabled.value!!) {
            log.w(TAG, "completeSet: someone isn't using the enabled value")
            return
        }
        val newRecord = SetRecord(weight.toDouble(), reps.toInt(),
                exerciseSet)
        exerciseRepo.storeSetRecord(newRecord)

        // TODO if this is superset part a then move to the next exercise
        if (exerciseSet.step.contains(".1")) {
            navigateRight()
            return
        } else if (exerciseSet.step.contains(".2")) {
            // TODO don't navigate left if this is the last set
            navigateLeft()
        }
        timer = object : CountDownTimer(
                (exerciseSet.rest * 1000).toLong(),
                50) {
            override fun onTick(millisUntilFinished: Long) {
                //updateRestTimerProgress(millisUntilFinished / 1000.0);
                restRemaining.value = millisUntilFinished / 1000.0
            }

            override fun onFinish() {
                timer = null
                //setTimerText(exerciseSets.getValue().get(exerciseIndex.getValue()));
                timerMutableLiveData.value = null
            }
        }
        //setTimerText(exerciseSet);
        timerMutableLiveData.value = timer
        timer!!.start()
    }

    companion object {
        private const val TAG = "ExerciseViewModel"
        const val defaultDbWeight = 25.0
        const val defaultBbWeight = 45.0
        const val defaultBodyweight = 45.0
        private fun formatWeightDisplay(value: Double): String {
            val cleanValue = (value * 2).roundToInt() / 2.0
            return String.format(Locale.getDefault(), "%.1f", if (cleanValue < 0) 0.0 else cleanValue)
        }

        private fun formatRepsDisplay(value: Int): String {
            return String.format(Locale.getDefault(), "%d", if (value < 0) 0 else value)
        }
    }

    init {
        exerciseSets.addSource(exerciseRepo.exercises) { exercises: List<ExerciseSet> ->
            exerciseSets.value = exercises
        }
        weightDisplayValue.addSource(weightSeedValue) { v: String -> weightDisplayValue.setValue(v) }
        weightDisplayValue.addSource(weightDisplayValue) { v: String ->
            // is this going to be heavy?
            log.d(TAG, "setupWeightAndRepsTransforms: reviewing changing weightDisplayValue")
            val value: Double
            value = try {
                v.toDouble()
            } catch (ex: Exception) {
                log.e(TAG, "setupWeightAndRepsTransforms: ", ex)
                0.0
            }
            if (value != (value * 2).roundToInt() / 2.0) {
                log.d(TAG, "setupWeightAndRepsTransforms: had to reformat weightDisplayValue")
                weightDisplayValue.value = formatWeightDisplay(value)
            }
        }
        repsDisplayValue.addSource(repsSeedValue) { v: String -> repsDisplayValue.setValue(v) }
        repsDisplayValue.addSource(repsDisplayValue) { v: String ->
            // is this going to be heavy?
            log.d(TAG, "setupWeightAndRepsTransforms: reviewing changing repsDisplayValue")
            val value: Int
            value = try {
                v.toInt()
            } catch (ex: Exception) {
                log.e(TAG, "setupWeightAndRepsTransforms: ", ex)
                0
            }
            if (value < 0) {
                log.d(TAG, "setupWeightAndRepsTransforms: had to reformat repsDisplayValue")
                repsDisplayValue.value = formatRepsDisplay(value)
            }
        }
        showAsDouble.addSource(isBarbellExercise) { isBarbellExercise ->
            showAsDouble.value = isBarbellExercise
        }
    }
}