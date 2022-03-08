@file:OptIn(FlowPreview::class)

package com.litus_animae.refitted.compose.exercise

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import arrow.core.Option
import arrow.core.getOrElse
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.ExerciseSetView
import com.litus_animae.refitted.compose.LoadingView
import com.litus_animae.refitted.compose.state.Record
import com.litus_animae.refitted.compose.util.Theme
import com.litus_animae.refitted.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@FlowPreview
@Composable
fun ExerciseDetails(
    model: ExerciseViewModel = viewModel(),
    setHistoryList: (Flow<PagingData<SetRecord>>) -> Unit,
    setContextMenu: (@Composable RowScope.() -> Unit) -> Unit
) {
    val (index, setIndex) = remember { mutableStateOf(0) }
    val instructions by model.exercises.collectAsState(initial = emptyList(), Dispatchers.IO)
    val instruction by derivedStateOf { instructions.getOrNull(index) }
    val exerciseSet by instruction?.set?.collectAsState(initial = null, Dispatchers.IO)
        ?: remember { mutableStateOf<ExerciseSet?>(null) }
    val records = remember { mutableStateListOf<SnapshotStateList<Record>>() }

    if (exerciseSet == null) {
        Surface(Modifier.fillMaxSize()) {
            LoadingView()
        }
    } else {
        val currentSet = exerciseSet!!
        var storedRecords by remember(currentSet) {
            mutableStateOf(
                ExerciseRecord(
                    currentSet,
                    emptyFlow(),
                    emptyFlow(),
                    emptyFlow()
                )
            )
        }
        LaunchedEffect(currentSet) {
            setContextMenu { instruction?.let { this.ExerciseContextMenu(it) } }
            model.recordForExercise(currentSet).collect {
                storedRecords = it
                setHistoryList(it.allSets)
            }
        }
        // TODO update default weight with best default
        val defaultRecord by derivedStateOf { Record(25.0, currentSet.reps, currentSet) }
        val lastExerciseRecord by storedRecords.latestSet.collectAsState(
            initial = null,
            Dispatchers.IO
        )
        val todayExerciseRecords by storedRecords.sets.collectAsState(
            initial = emptyList(),
            Dispatchers.IO
        )
        val todayRecords by derivedStateOf {
            todayExerciseRecords.map {
                Record(it.weight, it.reps, currentSet, stored = true)
            }
        }
        val lastStoredRecord by derivedStateOf {
            Option.fromNullable(lastExerciseRecord).map {
                Record(it.weight, it.reps, currentSet)
            }.getOrElse { defaultRecord }
        }
        val setRecords by derivedStateOf {
            records.getOrElse(index) {
                if (todayRecords.isEmpty()) mutableStateListOf(lastStoredRecord)
                else mutableStateListOf(*todayRecords.toTypedArray())
            }
        }
        val currentRecord by derivedStateOf {
            val unsavedRecord by derivedStateOf { setRecords.firstOrNull { !it.stored } }
            val lastRecord by derivedStateOf { setRecords.last() }
            unsavedRecord ?: lastRecord
        }
        val setsCompleted by storedRecords.setsCount.collectAsState(initial = 0, Dispatchers.IO)

        val scope = rememberCoroutineScope()
        DetailView(
            index,
            instructions.size - 1,
            currentSet,
            currentRecord,
            numCompleted = setsCompleted,
            updateIndex = { newIndex, updatedRecord ->
                saveRecordInState(records, index, setRecords, updatedRecord)
                setIndex(newIndex)
            },
            onSave = { updatedRecord ->
                val savedRecord = updatedRecord.copy(stored = true)
                saveRecordInState(records, index, setRecords, savedRecord)
                scope.launch {
                    model.saveExercise(
                        SetRecord(
                            savedRecord.weight,
                            savedRecord.reps,
                            savedRecord.set
                        )
                    )
                }
            })
    }
}

private fun saveRecordInState(
    records: SnapshotStateList<SnapshotStateList<Record>>,
    index: Int,
    setRecords: SnapshotStateList<Record>,
    savedRecord: Record
) {
    if (records.getOrNull(index) != null)
        if (setRecords.firstOrNull { !it.stored } != null)
            setRecords[setRecords.lastIndex] = savedRecord
        else
            setRecords.add(savedRecord)
    else
        records.add(index, mutableStateListOf(savedRecord))
}

@Preview(showBackground = true)
@Composable
fun PreviewDetailView(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
    MaterialTheme(Theme.darkColors) {
        Column {
            DetailView(
                index = 0,
                maxIndex = 2,
                exerciseSet = exerciseSet,
                record = Record(25.0, exerciseSet.reps, exerciseSet),
                numCompleted = 1,
                updateIndex = { _, _ -> },
                onSave = { })
        }
    }
}

@Composable
fun DetailView(
    index: Int,
    maxIndex: Int,
    exerciseSet: ExerciseSet,
    record: Record,
    numCompleted: Int,
    updateIndex: (Int, Record) -> Unit,
    onSave: (Record) -> Unit
) {
    when (LocalConfiguration.current.orientation) {
        Configuration.ORIENTATION_LANDSCAPE ->
            Row(Modifier.padding(16.dp)) {
                ExerciseDetails(exerciseSet, Modifier.weight(1f))
                ExerciseSetView(
                    exerciseSet,
                    record,
                    numCompleted,
                    index,
                    maxIndex,
                    updateIndex,
                    onSave,
                    Modifier.weight(1f)
                )
            }
        else ->
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.weight(1f)) {
                    ExerciseDetails(exerciseSet)
                }
                Row(Modifier.weight(1f)) {
                    ExerciseSetView(
                        exerciseSet,
                        record,
                        numCompleted,
                        index,
                        maxIndex,
                        updateIndex,
                        onSave
                    )
                }
            }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewExerciseDetails(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
    MaterialTheme(Theme.darkColors) {
        Column {
            this.ExerciseDetails(
                exerciseSet = exerciseSet
            )
        }
    }
}

@Composable
private fun RowScope.ExerciseDetails(
    exerciseSet: ExerciseSet?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        ExerciseDetails(exerciseSet)
    }
}

@Composable
private fun ColumnScope.ExerciseDetails(exerciseSet: ExerciseSet?) {
    Row {
        if (exerciseSet != null)
            Text(text = exerciseSet.exerciseName, style = MaterialTheme.typography.h6)
    }
    Row(Modifier.padding(vertical = 5.dp)) {
        Column(Modifier.weight(1f)) {
            val label = stringResource(id = R.string.target_reps)
            val toFailureLabel = stringResource(id = R.string.to_failure)
            when {
                exerciseSet == null -> Text(label)
                exerciseSet.reps < 0 -> Text("$label $toFailureLabel")
                else -> Text("$label ${exerciseSet.reps}")
            }
        }
        Column(Modifier.weight(1f)) {
            val label = stringResource(id = R.string.target_sets)
            if (exerciseSet == null)
                Text(label)
            else Text("$label ${exerciseSet.sets}")
        }
    }
    val scrollState = rememberScrollState()
    Row {
        // TODO is there a way to show the scrollbar to indicate scrollability?
        Column(Modifier.verticalScroll(scrollState)) {
            Row(Modifier.padding(vertical = 5.dp)) {
                if (exerciseSet != null) {
                    val exercise by exerciseSet.exercise.collectAsState()
                    Text(exercise?.description ?: "")
                }
            }
            Row(Modifier.padding(vertical = 5.dp)) {
                Text(exerciseSet?.note ?: "")
            }
        }
    }
}

@Composable
fun RowScope.ExerciseContextMenu(instruction: ExerciseViewModel.ExerciseInstruction) {
    if (instruction.hasAlternate) {
        Column(Modifier
            .clickable { instruction.activateNextAlternate() }
            .fillMaxHeight(),
            verticalArrangement = Arrangement.Center) {
            // TODO localize
            Text("Alternate", Modifier.padding(end = 5.dp))
        }
    }
}
