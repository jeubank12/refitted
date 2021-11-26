package com.litus_animae.refitted.models;

import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.models.util.TestDataSourceFactory
import com.litus_animae.util.InstantExecutorExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(InstantExecutorExtension::class)
class ExerciseRecordTest {
    private var testExerciseSet = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
            id = "1.5",
            name = "Tricep_Alternating Woodchopper Pushdowns"
    )), MutableStateFlow(null)
    )
    private val testSets = mutableListOf<SetRecord>()
    private val flowList = MutableStateFlow<List<SetRecord>>(testSets)

    private val testExerciseRecord = ExerciseRecord(testExerciseSet,
        flowList.map { list: List<SetRecord> -> list.last()},
        TestDataSourceFactory(testSets),
        flowList)

    @Test
    fun getSetsCountEmpty() {
        runBlocking {
            assertThat(flowList.value).isNotNull()
            assertThat(flowList.value.size).isEqualTo(0)
            assertThat(testExerciseRecord.setsCount.first()).isEqualTo(0)
        }
    }

    @Test
    fun getSetsCountNull() {
        val testExerciseRecord = ExerciseRecord(testExerciseSet,
                flowList.map{ list: List<SetRecord> -> list.last()},
                TestDataSourceFactory(testSets),
                MutableStateFlow(emptyList())
        )
        runBlocking {
            assertThat(testExerciseRecord.setsCount.first()).isEqualTo(0)
        }
    }

    @Test
    fun getSetsCount() {
        runBlocking {
            testSets.add(SetRecord(0.0, 0, testExerciseSet))
            testSets.add(SetRecord(0.0, 1, testExerciseSet))
            assertThat(testExerciseRecord.setsCount.first()).isEqualTo(2)
        }
    }

    @Test
    fun getLastSet() {
        runBlocking {
            testSets.add(SetRecord(0.0, 0, testExerciseSet))
            testSets.add(SetRecord(0.0, 1, testExerciseSet))
            assertThat(testExerciseRecord.getSet(-1).first()?.reps).isEqualTo(1)
        }
    }

    @Test
    fun getFirstSet() {
        runBlocking {
            testSets.add(SetRecord(0.0, 0, testExerciseSet))
            testSets.add(SetRecord(0.0, 1, testExerciseSet))
            assertThat(testExerciseRecord.getSet(0).first()?.reps).isEqualTo(0)
        }
    }

    @Test
    fun getBeyondLastSet() {
        runBlocking {
            testSets.add(SetRecord(0.0, 0, testExerciseSet))
            testSets.add(SetRecord(0.0, 1, testExerciseSet))
            assertThat(testExerciseRecord.getSet(2).first()).isNull()
        }
    }
}