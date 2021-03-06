package com.litus_animae.refitted.models;

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.models.util.TestDataSourceFactory
import com.litus_animae.util.InstantExecutorExtension
import com.litus_animae.util.getOrAwaitValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(InstantExecutorExtension::class)
class ExerciseRecordTest {
    private var testExerciseSet = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
            id = "1.5",
            name = "Tricep_Alternating Woodchopper Pushdowns"
    )), MutableLiveData())
    private val testSets = mutableListOf<SetRecord>()
    private val liveDataList = MutableLiveData<List<SetRecord>>(testSets)

    private val testExerciseRecord = ExerciseRecord(testExerciseSet,
        Transformations.map(liveDataList){ list: List<SetRecord> -> list.last()},
        TestDataSourceFactory(testSets),
        liveDataList)

    @Test
    fun getSetsCountEmpty() {
        assertThat(liveDataList.value).isNotNull()
        assertThat(liveDataList.value?.size).isEqualTo(0)
        assertThat(testExerciseRecord.setsCount.getOrAwaitValue()).isEqualTo(0)
    }

    @Test
    fun getSetsCountNull() {
        val testExerciseRecord = ExerciseRecord(testExerciseSet,
                Transformations.map(liveDataList){ list: List<SetRecord> -> list.last()},
                TestDataSourceFactory(testSets),
                MutableLiveData<List<SetRecord>>(null))
        assertThat(testExerciseRecord.setsCount.getOrAwaitValue()).isEqualTo(0)
    }

    @Test
    fun getSetsCount() {
        testSets.add(SetRecord(0.0, 0, testExerciseSet))
        testSets.add(SetRecord(0.0, 1, testExerciseSet))
        assertThat(testExerciseRecord.setsCount.getOrAwaitValue()).isEqualTo(2)
    }

    @Test
    fun getLastSet() {
        testSets.add(SetRecord(0.0, 0, testExerciseSet))
        testSets.add(SetRecord(0.0, 1, testExerciseSet))
        assertThat(testExerciseRecord.getSet(-1).getOrAwaitValue()?.reps).isEqualTo(1)
    }

    @Test
    fun getFirstSet() {
        testSets.add(SetRecord(0.0, 0, testExerciseSet))
        testSets.add(SetRecord(0.0, 1, testExerciseSet))
        assertThat(testExerciseRecord.getSet(0).getOrAwaitValue()?.reps).isEqualTo(0)
    }

    @Test
    fun getBeyondLastSet() {
        testSets.add(SetRecord(0.0, 0, testExerciseSet))
        testSets.add(SetRecord(0.0, 1, testExerciseSet))
        assertThat(testExerciseRecord.getSet(2).getOrAwaitValue()).isNull()
    }
}