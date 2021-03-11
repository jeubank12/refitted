package com.litus_animae.refitted.models

import android.view.View
import androidx.lifecycle.MutableLiveData
import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.R
import com.litus_animae.refitted.data.InMemoryExerciseRepository
import com.litus_animae.refitted.models.ExerciseViewModelDeprecated.Companion.defaultBbWeight
import com.litus_animae.refitted.models.ExerciseViewModelDeprecated.Companion.defaultBodyweight
import com.litus_animae.refitted.models.ExerciseViewModelDeprecated.Companion.defaultDbWeight
import com.litus_animae.refitted.models.util.TestDataSourceFactory
import com.litus_animae.refitted.util.TestLogUtil
import com.litus_animae.util.InstantExecutorExtension
import com.litus_animae.util.getOrAwaitValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(InstantExecutorExtension::class)
internal class ExerciseViewModelDeprecatedTest {

    @BeforeEach
    fun setUp() {
    }

    @Test
    fun intializeIsLoading() {
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(), TestLogUtil)
        assertThat(model.isLoading.getOrAwaitValue()).isEqualTo(View.VISIBLE)
    }

    @Test
    fun intializeDoesNotHasLeft() {
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(), TestLogUtil)
        assertThat(model.hasLeft.getOrAwaitValue()).isEqualTo(View.GONE)
    }

    @Test
    fun intializeHasLeftSecondExercise() {
        val exerciseA = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.1"
        )), MutableLiveData())
        val exerciseB = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.2"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
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
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(), TestLogUtil)
        assertThat(model.hasRight.getOrAwaitValue()).isEqualTo(View.VISIBLE)
    }

    @Test
    fun intializeDoesNotHasRightSecondExercise() {
        val exerciseA = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.1"
        )), MutableLiveData())
        val exerciseB = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.2"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
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
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(
            ExerciseSet(RoomExerciseSet(DynamoExerciseSet()), MutableLiveData()))
    }

    @Test
    fun intializeExerciseWithValue() {
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                name = "abcd"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(listOf(exercise)), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(exercise)
    }

    @Test
    fun intializeWeightDisplay() {
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("$defaultDbWeight")
    }

    @Test
    fun intializeRepsDisplay() {
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.repsDisplayValue.getOrAwaitValue()).isEqualTo("0")
    }

    @Test
    fun intializeTargetRepsNone() {
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(), TestLogUtil)
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
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(
                initialExercises = listOf(ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                        reps = -1
                )), MutableLiveData()))
        ), TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.targetExerciseReps.getOrAwaitValue().getParameters().getOrNull(0)).isEqualTo(R.string.to_failure)
    }

    @Test
    fun intializeTargetReps() {
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(
                initialExercises = listOf(ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                        reps = 2
                )), MutableLiveData()))
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
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(
                initialExercises = listOf(ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        isToFailure = true
                )), MutableLiveData()))
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
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(
                initialExercises = listOf(ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsUnit = "abcd"
                )), MutableLiveData()))
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
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(
                initialExercises = listOf(ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsUnit = "abcd",
                        isToFailure = true
                )), MutableLiveData()))
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
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(
                initialExercises = listOf(ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsRange = 2
                )), MutableLiveData()))
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
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(
                initialExercises = listOf(ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsRange = 2,
                        isToFailure = true
                )), MutableLiveData()))
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
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(
                initialExercises = listOf(ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsRange = 2,
                        repsUnit = "abcd"
                )), MutableLiveData()))
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
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(
                initialExercises = listOf(ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                        reps = 2,
                        repsRange = 2,
                        repsUnit = "abcd",
                        isToFailure = true
                )), MutableLiveData()))
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
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(
                initialExercises = listOf(exercise),
                initialRecords = listOf(ExerciseRecord(
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
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(
                initialExercises = listOf(exercise),
                initialRecords = listOf(ExerciseRecord(
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
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        model.weightDisplayValue.getOrAwaitValue()
        model.updateWeightDisplay(1.0)
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("${defaultDbWeight + 1.0}")
    }

    @Test
    fun updateWeightDisplayLessThanZero() {
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        model.weightDisplayValue.getOrAwaitValue()
        model.updateWeightDisplay(-100.0)
        assertThat(model.weightDisplayValue.getOrAwaitValue()).isEqualTo("0.0")
    }

    @Test
    fun updateRepsDisplay() {
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        model.repsDisplayValue.getOrAwaitValue()
        model.updateRepsDisplay(true)
        assertThat(model.repsDisplayValue.getOrAwaitValue()).isEqualTo("1")
    }

    @Test
    fun updateRepsDisplayLessThanZero() {
        val model = ExerciseViewModelDeprecated(InMemoryExerciseRepository(), TestLogUtil)
        model.loadExercises("", "")
        model.repsDisplayValue.getOrAwaitValue()
        model.updateRepsDisplay(false)
        assertThat(model.repsDisplayValue.getOrAwaitValue()).isEqualTo("0")
    }

    @Test
    fun weightDisplayTimeSeconds(){
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                repsUnit = "Seconds"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exercise),
                        initialRecords = listOf(ExerciseRecord(
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
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                repsUnit = "Minutes",
                name = "x_DB BB DUMBBELL BARBELL"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exercise),
                        initialRecords = listOf(ExerciseRecord(
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
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_DB BB BARBELL",
                note = "BB BARBELL"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exercise),
                        initialRecords = listOf(ExerciseRecord(
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
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_DumBbell BB BARBELL",
                note = "BB BARBELL"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exercise),
                        initialRecords = listOf(ExerciseRecord(
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
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_BB",
                note = "Dumbbell Db"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exercise),
                        initialRecords = listOf(ExerciseRecord(
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
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_Barbell",
                note = "Dumbbell Db"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exercise),
                        initialRecords = listOf(ExerciseRecord(
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
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_None of the above",
                note = "Db BB Barbell"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exercise),
                        initialRecords = listOf(ExerciseRecord(
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
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_None of the above",
                note = "Dumbbell BB Barbell"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exercise),
                        initialRecords = listOf(ExerciseRecord(
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
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_None of the above",
                note = "BB"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exercise),
                        initialRecords = listOf(ExerciseRecord(
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
        val exercise = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                reps = 2,
                repsRange = 2,
                name = "x_None of the above",
                note = "BB"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exercise),
                        initialRecords = listOf(ExerciseRecord(
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
        val exerciseA = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.1.a"
        )), MutableLiveData())
        val exerciseB = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.1.b"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exerciseA, exerciseB)
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.exercise.getOrAwaitValue().hasAlternate).isTrue()
    }

    @Test
    fun initializeNoAlternate() {
        val exerciseA = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.1"
        )), MutableLiveData())
        val exerciseB = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.2"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exerciseA, exerciseB)
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.exercise.getOrAwaitValue().hasAlternate).isFalse()
    }

    @Test
    fun swapToAlternate() {
        val exerciseA = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.1.a"
        )), MutableLiveData())
        val exerciseB = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.1.b"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exerciseA, exerciseB)
                ),
                TestLogUtil)
        model.loadExercises("", "")
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(exerciseA)
        model.swapToAlternate()
        assertThat(model.exercise.getOrAwaitValue()).isEqualTo(exerciseB)
    }

    @Test
    fun navigateLeft() {
        val exerciseA = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.1"
        )), MutableLiveData())
        val exerciseB = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.2"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exerciseA, exerciseB)
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
        val exerciseA = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.1"
        )), MutableLiveData())
        val exerciseB = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.2"
        )), MutableLiveData())
        val model = ExerciseViewModelDeprecated(
                InMemoryExerciseRepository(
                        initialExercises = listOf(exerciseA, exerciseB)
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