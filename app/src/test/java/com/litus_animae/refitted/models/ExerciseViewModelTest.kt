package com.litus_animae.refitted.models

import android.view.View
import androidx.lifecycle.MutableLiveData
import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.R
import com.litus_animae.refitted.data.InMemoryExerciseRepository
import com.litus_animae.refitted.models.ExerciseViewModel.Companion.defaultBbWeight
import com.litus_animae.refitted.models.ExerciseViewModel.Companion.defaultBodyweight
import com.litus_animae.refitted.models.ExerciseViewModel.Companion.defaultDbWeight
import com.litus_animae.refitted.models.util.TestDataSourceFactory
import com.litus_animae.refitted.util.TestLogUtil
import com.litus_animae.util.InstantExecutorExtension
import com.litus_animae.util.getOrAwaitValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(InstantExecutorExtension::class)
internal class ExerciseViewModelTest {

    @BeforeEach
    fun setUp() {
    }

    @Test
    fun intializeIsLoading() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(), TestLogUtil)
        assertThat(model.isLoading.getOrAwaitValue()).isEqualTo(View.VISIBLE)
    }

    @Test
    fun intializeDoesNotHasLeft() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(), TestLogUtil)
        assertThat(model.hasLeft.getOrAwaitValue()).isEqualTo(View.GONE)
    }

    @Test
    fun intializeHasLeftSecondExercise() {
        val exerciseA = ExerciseSet(DynamoExerciseSet(
                id = "1.1"
        ))
        val exerciseB = ExerciseSet(DynamoExerciseSet(
                id = "1.2"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(listOf(exerciseA, exerciseB)),
                TestLogUtil)
        model.loadExercises("", "")
        model.exercise.getOrAwaitValue()
        model.navigateRight()
        model.exercise.getOrAwaitValue()
        assertThat(model.hasLeft.getOrAwaitValue()).isEqualTo(View.VISIBLE)
    }

    @Test
    fun intializeHasRight() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(), TestLogUtil)
        assertThat(model.hasRight.getOrAwaitValue()).isEqualTo(View.VISIBLE)
    }

    @Test
    fun intializeDoesNotHasRightSecondExercise() {
        val exerciseA = ExerciseSet(DynamoExerciseSet(
                id = "1.1"
        ))
        val exerciseB = ExerciseSet(DynamoExerciseSet(
                id = "1.2"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(listOf(exerciseA, exerciseB)),
                TestLogUtil)
        model.loadExercises("", "")
        model.exercise.getOrAwaitValue()
        model.navigateRight()
        model.exercise.getOrAwaitValue()
        assertThat(model.hasRight.getOrAwaitValue()).isEqualTo(View.GONE)
    }

    @Test
    fun intializeExercise() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(ExerciseSet(DynamoExerciseSet()))
    }

    @Test
    fun intializeExerciseWithValue() {
        val exercise = ExerciseSet(DynamoExerciseSet(
                name = "abcd"
        ))
        val model = ExerciseViewModel(InMemoryExerciseRepository(listOf(exercise)), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(exercise)
    }

    @Test
    fun intializeWeightDisplay() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultDbWeight")
    }

    @Test
    fun intializeRepsDisplay() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.repsDisplayValue.getOrAwaitValue()).isEqualTo("0")
    }

    @Test
    fun intializeTargetRepsNone() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        val params = model.targetExerciseReps.getOrAwaitValue().getParameters()
        assertThat(params.getOrNull(0)).isEqualTo(R.array.exercise_reps)
        assertThat(params.getOrNull(1)).isEqualTo(0)
        assertThat(params.getOrNull(2)).isEqualTo(0)
        assertThat(params.getOrNull(3)).isEqualTo(0)
        assertThat(params.getOrNull(4)).isEqualTo("")
    }

    @Test
    fun intializeTargetRepsFailure() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(
                exercises = listOf(ExerciseSet(DynamoExerciseSet(
                        reps = -1
                )))
        ), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.targetExerciseReps.getOrAwaitValue().getParameters().getOrNull(0)).isEqualTo(R.string.to_failure)
    }

    @Test
    fun intializeTargetReps() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(
                exercises = listOf(ExerciseSet(DynamoExerciseSet(
                        reps = 2
                )))
        ), TestLogUtil)
        model.loadExercises("", "")
        val params = model.targetExerciseReps.getOrAwaitValue().getParameters()
        assertThat(params.getOrNull(0)).isEqualTo(R.array.exercise_reps)
        assertThat(params.getOrNull(1)).isEqualTo(0)
        assertThat(params.getOrNull(2)).isEqualTo(2)
        assertThat(params.getOrNull(3)).isEqualTo(2)
        assertThat(params.getOrNull(4)).isEqualTo("")
    }

    @Test
    fun intializeTargetRepsWithToFailure() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(
                exercises = listOf(ExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        isToFailure = true
                )))
        ), TestLogUtil)
        model.loadExercises("", "")
        val params = model.targetExerciseReps.getOrAwaitValue().getParameters()
        assertThat(params.getOrNull(0)).isEqualTo(R.array.exercise_reps)
        assertThat(params.getOrNull(1)).isEqualTo(1)
        assertThat(params.getOrNull(2)).isEqualTo(2)
        assertThat(params.getOrNull(3)).isEqualTo(2)
        assertThat(params.getOrNull(4)).isEqualTo("")
    }

    @Test
    fun intializeTargetRepsWithUnit() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(
                exercises = listOf(ExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsUnit = "abcd"
                )))
        ), TestLogUtil)
        model.loadExercises("", "")
        val params = model.targetExerciseReps.getOrAwaitValue().getParameters()
        assertThat(params.getOrNull(0)).isEqualTo(R.array.exercise_reps)
        assertThat(params.getOrNull(1)).isEqualTo(2)
        assertThat(params.getOrNull(2)).isEqualTo(2)
        assertThat(params.getOrNull(3)).isEqualTo(2)
        assertThat(params.getOrNull(4)).isEqualTo("abcd")
    }

    @Test
    fun intializeTargetRepsWithUnitToFailure() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(
                exercises = listOf(ExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsUnit = "abcd",
                        isToFailure = true
                )))
        ), TestLogUtil)
        model.loadExercises("", "")
        val params = model.targetExerciseReps.getOrAwaitValue().getParameters()
        assertThat(params.getOrNull(0)).isEqualTo(R.array.exercise_reps)
        assertThat(params.getOrNull(1)).isEqualTo(3)
        assertThat(params.getOrNull(2)).isEqualTo(2)
        assertThat(params.getOrNull(3)).isEqualTo(2)
        assertThat(params.getOrNull(4)).isEqualTo("abcd")
    }

    @Test
    fun intializeTargetRepsRange() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(
                exercises = listOf(ExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsRange = 2
                )))
        ), TestLogUtil)
        model.loadExercises("", "")
        val params = model.targetExerciseReps.getOrAwaitValue().getParameters()
        assertThat(params.getOrNull(0)).isEqualTo(R.array.exercise_reps_range)
        assertThat(params.getOrNull(1)).isEqualTo(0)
        assertThat(params.getOrNull(2)).isEqualTo(2)
        assertThat(params.getOrNull(3)).isEqualTo(4)
        assertThat(params.getOrNull(4)).isEqualTo("")
    }

    @Test
    fun intializeTargetRepsRangeWithToFailure() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(
                exercises = listOf(ExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsRange = 2,
                        isToFailure = true
                )))
        ), TestLogUtil)
        model.loadExercises("", "")
        val params = model.targetExerciseReps.getOrAwaitValue().getParameters()
        assertThat(params.getOrNull(0)).isEqualTo(R.array.exercise_reps_range)
        assertThat(params.getOrNull(1)).isEqualTo(1)
        assertThat(params.getOrNull(2)).isEqualTo(2)
        assertThat(params.getOrNull(3)).isEqualTo(4)
        assertThat(params.getOrNull(4)).isEqualTo("")
    }

    @Test
    fun intializeTargetRepsRangeWithUnit() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(
                exercises = listOf(ExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsRange = 2,
                        repsUnit = "abcd"
                )))
        ), TestLogUtil)
        model.loadExercises("", "")
        val params = model.targetExerciseReps.getOrAwaitValue().getParameters()
        assertThat(params.getOrNull(0)).isEqualTo(R.array.exercise_reps_range)
        assertThat(params.getOrNull(1)).isEqualTo(2)
        assertThat(params.getOrNull(2)).isEqualTo(2)
        assertThat(params.getOrNull(3)).isEqualTo(4)
        assertThat(params.getOrNull(4)).isEqualTo("abcd")
    }

    @Test
    fun intializeTargetRepsRangeWithUnitToFailure() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(
                exercises = listOf(ExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsRange = 2,
                        repsUnit = "abcd",
                        isToFailure = true
                )))
        ), TestLogUtil)
        model.loadExercises("", "")
        val params = model.targetExerciseReps.getOrAwaitValue().getParameters()
        assertThat(params.getOrNull(0)).isEqualTo(R.array.exercise_reps_range)
        assertThat(params.getOrNull(1)).isEqualTo(3)
        assertThat(params.getOrNull(2)).isEqualTo(2)
        assertThat(params.getOrNull(3)).isEqualTo(4)
        assertThat(params.getOrNull(4)).isEqualTo("abcd")
    }

    @Test
    fun initializeRecord(){

    }

    @Test
    fun initializeRepsDisplay(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2
        ))
        val model = ExerciseViewModel(InMemoryExerciseRepository(
                exercises = listOf(exercise),
                records = listOf(ExerciseRecord(
                        targetSet = exercise,
                        latestSet = MutableLiveData(null),
                        sets = MutableLiveData(emptyList()),
                        allSets = TestDataSourceFactory(emptyList())
                ))
        ), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.repsDisplayValue.getOrAwaitValue()).isEqualTo("2")
    }

    @Test
    fun initializeRepsDisplayRange(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2
        ))
        val model = ExerciseViewModel(InMemoryExerciseRepository(
                exercises = listOf(exercise),
                records = listOf(ExerciseRecord(
                        targetSet = exercise,
                        latestSet = MutableLiveData(null),
                        sets = MutableLiveData(emptyList()),
                        allSets = TestDataSourceFactory(emptyList())
                ))
        ), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.repsDisplayValue.getOrAwaitValue()).isEqualTo("4")

    }
//
//    @Test
//    fun onCleared() {
//        val mockRepository = mock(ExerciseRepository::class.java)
//        val model = ExerciseViewModel(mockRepository, TestLogUtil)
//        model.onCleared()
//        verify(mockRepository).shutdown()
//    }

    @Test
    fun updateWeightDisplay() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        model.weightDisplayValue.getOrAwaitValue()
        model.updateWeightDisplay(1.0)
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("${defaultDbWeight + 1.0}")
    }

    @Test
    fun updateWeightDisplayLessThanZero() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        model.weightDisplayValue.getOrAwaitValue()
        model.updateWeightDisplay(-100.0)
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("0.0")
    }

    @Test
    fun updateRepsDisplay() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        model.repsDisplayValue.getOrAwaitValue()
        model.updateRepsDisplay(true)
        assertThat(model.repsDisplayValue.getOrAwaitValue()).isEqualTo("1")
    }

    @Test
    fun updateRepsDisplayLessThanZero() {
        val model = ExerciseViewModel(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        model.repsDisplayValue.getOrAwaitValue()
        model.updateRepsDisplay(false)
        assertThat(model.repsDisplayValue.getOrAwaitValue()).isEqualTo("0")
    }

    @Test
    fun weightDisplayTimeSeconds(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                repsUnit = "Seconds"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exercise),
                        records = listOf(ExerciseRecord(
                                targetSet = exercise,
                                latestSet = MutableLiveData(null),
                                sets = MutableLiveData(emptyList()),
                                allSets = TestDataSourceFactory(emptyList())
                        ))
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultBodyweight")
    }

    @Test
    fun weightDisplayTimeMinutes(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                repsUnit = "Minutes",
                name = "x_DB BB DUMBBELL BARBELL"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exercise),
                        records = listOf(ExerciseRecord(
                                targetSet = exercise,
                                latestSet = MutableLiveData(null),
                                sets = MutableLiveData(emptyList()),
                                allSets = TestDataSourceFactory(emptyList())
                        ))
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultBodyweight")
    }

    @Test
    fun weightDisplayDbName(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_DB BB BARBELL",
                note = "BB BARBELL"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exercise),
                        records = listOf(ExerciseRecord(
                                targetSet = exercise,
                                latestSet = MutableLiveData(null),
                                sets = MutableLiveData(emptyList()),
                                allSets = TestDataSourceFactory(emptyList())
                        ))
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultDbWeight")
    }

    @Test
    fun weightDisplayDumbbellName(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_DumBbell BB BARBELL",
                note = "BB BARBELL"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exercise),
                        records = listOf(ExerciseRecord(
                                targetSet = exercise,
                                latestSet = MutableLiveData(null),
                                sets = MutableLiveData(emptyList()),
                                allSets = TestDataSourceFactory(emptyList())
                        ))
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultDbWeight")
    }

    @Test
    fun weightDisplayBbName(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_BB",
                note = "Dumbbell Db"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exercise),
                        records = listOf(ExerciseRecord(
                                targetSet = exercise,
                                latestSet = MutableLiveData(null),
                                sets = MutableLiveData(emptyList()),
                                allSets = TestDataSourceFactory(emptyList())
                        ))
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultBbWeight")
    }

    @Test
    fun weightDisplayBarbellName(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_Barbell",
                note = "Dumbbell Db"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exercise),
                        records = listOf(ExerciseRecord(
                                targetSet = exercise,
                                latestSet = MutableLiveData(null),
                                sets = MutableLiveData(emptyList()),
                                allSets = TestDataSourceFactory(emptyList())
                        ))
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultBbWeight")
    }

    @Test
    fun weightDisplayDbNote(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_None of the above",
                note = "Db BB Barbell"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exercise),
                        records = listOf(ExerciseRecord(
                                targetSet = exercise,
                                latestSet = MutableLiveData(null),
                                sets = MutableLiveData(emptyList()),
                                allSets = TestDataSourceFactory(emptyList())
                        ))
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultDbWeight")
    }

    @Test
    fun weightDisplayDumbbellNote(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_None of the above",
                note = "Dumbbell BB Barbell"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exercise),
                        records = listOf(ExerciseRecord(
                                targetSet = exercise,
                                latestSet = MutableLiveData(null),
                                sets = MutableLiveData(emptyList()),
                                allSets = TestDataSourceFactory(emptyList())
                        ))
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultDbWeight")
    }

    @Test
    fun weightDisplayBbNote(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_None of the above",
                note = "BB"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exercise),
                        records = listOf(ExerciseRecord(
                                targetSet = exercise,
                                latestSet = MutableLiveData(null),
                                sets = MutableLiveData(emptyList()),
                                allSets = TestDataSourceFactory(emptyList())
                        ))
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultBbWeight")
    }

    @Test
    fun weightDisplayBarbellNote(){
        val exercise = ExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_None of the above",
                note = "BB"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exercise),
                        records = listOf(ExerciseRecord(
                                targetSet = exercise,
                                latestSet = MutableLiveData(null),
                                sets = MutableLiveData(emptyList()),
                                allSets = TestDataSourceFactory(emptyList())
                        ))
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultBbWeight")
    }

    @Test
    fun initializeHasAlternate() {
        val exerciseA = ExerciseSet(DynamoExerciseSet(
                id = "1.1.a"
        ))
        val exerciseB = ExerciseSet(DynamoExerciseSet(
                id = "1.1.b"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exerciseA, exerciseB)
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.exercise.getOrAwaitValue().hasAlternate()).isTrue()
    }

    @Test
    fun initializeNoAlternate() {
        val exerciseA = ExerciseSet(DynamoExerciseSet(
                id = "1.1"
        ))
        val exerciseB = ExerciseSet(DynamoExerciseSet(
                id = "1.2"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exerciseA, exerciseB)
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.exercise.getOrAwaitValue().hasAlternate()).isFalse()
    }

    @Test
    fun swapToAlternate() {
        val exerciseA = ExerciseSet(DynamoExerciseSet(
                id = "1.1.a"
        ))
        val exerciseB = ExerciseSet(DynamoExerciseSet(
                id = "1.1.b"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exerciseA, exerciseB)
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(exerciseA)
        model.swapToAlternate()
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(exerciseB)
    }

    @Test
    fun navigateLeft() {
        val exerciseA = ExerciseSet(DynamoExerciseSet(
                id = "1.1"
        ))
        val exerciseB = ExerciseSet(DynamoExerciseSet(
                id = "1.2"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exerciseA, exerciseB)
                ),
                TestLogUtil)
        model.loadExercises("", "")
        model.exercise.getOrAwaitValue()
        model.navigateRight()
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(exerciseB)
        model.navigateLeft()
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(exerciseA)
    }

    @Test
    fun navigateRight() {
        val exerciseA = ExerciseSet(DynamoExerciseSet(
                id = "1.1"
        ))
        val exerciseB = ExerciseSet(DynamoExerciseSet(
                id = "1.2"
        ))
        val model = ExerciseViewModel(
                InMemoryExerciseRepository(
                        exercises = listOf(exerciseA, exerciseB)
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(exerciseA)
        model.navigateRight()
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(exerciseB)
    }

    @Test
    fun completeSet() {
        TODO()
    }

    @get:Test
    val completeSetMessage: Unit
        get() {
            TODO()
        }

    @get:Test
    val restMax: Unit
        get() {
            TODO()
        }

    @get:Test
    val restProgress: Unit
        get() {
            TODO()
        }

    @get:Test
    val restValue: Unit
        get() {
            TODO()
        }

    @get:Test
    val completeSetButtonEnabled: Unit
        get() {
            TODO()
        }

    @get:Test
    val currentRecord: Unit
        get() {
            TODO()
        }

    @get:Test
    val isBarbellExercise: Unit
        get() {
            TODO()
        }

    @get:Test
    val showAsDouble: Unit
        get() {
            TODO()
        }
}