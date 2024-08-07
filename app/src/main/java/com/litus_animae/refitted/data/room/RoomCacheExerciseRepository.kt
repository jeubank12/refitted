package com.litus_animae.refitted.data.room

import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.models.DayAndWorkout
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.Record
import com.litus_animae.refitted.models.SetRecord
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.progressiveZipWithPrevious
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Integer.min
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

@ExperimentalCoroutinesApi
@FlowPreview
class RoomCacheExerciseRepository @Inject constructor(
  private val roomProvider: RefittedRoomProvider,
  private val networkService: ExerciseSetNetworkService,
  private val log: LogUtil
) : ExerciseRepository {
  private val refittedRoom by lazy { roomProvider.refittedRoom }

  private val currentWorkout = MutableStateFlow("")
  override val workoutRecords = currentWorkout.flatMapLatest {
    refittedRoom.getExerciseDao().getDayCompletedSets(it)
  }

  private val exerciseState: MutableStateFlow<List<ExerciseSet>> = MutableStateFlow(emptyList())
  override val exercises = exerciseState.asStateFlow()

  private val pagingDataDiffer = AsyncPagingDataDiffer(
    diffCallback = object : DiffUtil.ItemCallback<ExerciseSet>() {
      override fun areItemsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
        return oldItem == newItem
      }

      override fun areContentsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
        return areItemsTheSame(oldItem, newItem)
      }

    },
    updateCallback = object : ListUpdateCallback {
      override fun onInserted(position: Int, count: Int) {
        if (count > 0) {
          updateExerciseState()
        }
      }

      override fun onRemoved(position: Int, count: Int) {
        if (count > 0) {
          updateExerciseState()
        }
      }

      override fun onMoved(fromPosition: Int, toPosition: Int) {
      }

      override fun onChanged(position: Int, count: Int, payload: Any?) {
        if (count > 0) {
          updateExerciseState()
        }
      }

    }
  )

  private fun updateExerciseState() {
    exerciseState.value = pagingDataDiffer.snapshot().items
  }

  private val _exercisesAreLoading = MutableStateFlow(true)
  override val exercisesAreLoading: StateFlow<Boolean> = _exercisesAreLoading.asStateFlow()

  override fun refreshExercises() {
    pagingDataDiffer.refresh()
  }

  override suspend fun loadExercises(day: String, workoutId: String) {
    _exercisesAreLoading.emit(true)
    log.i(TAG, "loadExercises: updating to workout $workoutId, day $day")
    val mediator =
      ExerciseSetPager(DayAndWorkout(day, workoutId), roomProvider, networkService, log)
    coroutineScope {

      launch { mediator.pagingData.collectLatest { pagingDataDiffer.submitData(it) } }

      launch {
        mediator.pagingData.collectLatest {
          pagingDataDiffer.loadStateFlow.collect {
            _exercisesAreLoading.emit(it.refresh is LoadState.Loading)
          }
        }
      }
    }
  }

  override val records = exercises.map(this::getRecordsForLoadedExercises)

  override suspend fun storeSetRecord(record: SetRecord) {
    withContext(Dispatchers.IO) {
      log.d(TAG, "storing set record")
      refittedRoom.getExerciseDao().storeExerciseRecord(record)
      log.d(TAG, "stored set record")
    }
  }

  override fun loadWorkoutRecords(workoutId: String) {
    currentWorkout.value = workoutId
  }

  private fun getRecordsForLoadedExercises(loadedExercises: List<ExerciseSet>): List<ExerciseRecord> {
    log.i(
      TAG,
      "getRecordsForLoadedExercises: detected ${loadedExercises.size} new exercises, loading records"
    )
    // FIXME this should be a real timezone?
    val tonightMidnight = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.ofHours(0))
    val recordObjects = loadedExercises.map { e ->
      val defaultReps = when {
        e.repsUnit.isNotBlank() -> 10
        e.sets < 0 && e.reps(0) < 0 -> 10
        e.sets < 0 -> min(10, e.reps(0))
        else -> e.reps(0)
      }
      // TODO (#8) appropriate default weights
      val defaultRecord = Record(
        weight = 25.0,
        defaultReps,
        e,
        Instant.ofEpochMilli(0)
      )
      val currentRecords = refittedRoom.getExerciseDao()
        .getSetRecords(tonightMidnight, e.exerciseName, e.id)
        .map {
          it.asSequence()
            .progressiveZipWithPrevious { lastRecord: Record?, setRecord ->
              Record(
                setRecord.weight, setRecord.reps, e, setRecord.completed,
                setRecord.reps + (lastRecord?.cumulativeReps ?: 0),
                stored = true
              )
            }.toList()
        }
      val latestRecord =
        combine(
          currentRecords,
          refittedRoom.getExerciseDao().getLatestSetRecord(e.exerciseName)
        ) { todayRecords, latestRecord ->
          todayRecords.lastOrNull() ?: latestRecord?.let {
            // here we know that the exercise has not been performed today
            // reps should not necessarily be blindly copied from the last set
            val reps = when {
              e.repsUnit.isNotBlank() && e.id == it.targetSet -> it.reps
              e.repsUnit.isNotBlank() -> 10
              e.reps(0) < 0 -> it.reps
              e.sets < 0 -> min(10, e.reps(0))
              else -> e.reps(0)
            }
            Record(it.weight, reps, e, it.completed)
          }
        }.mapNotNull { it }
      ExerciseRecord(
        e,
        defaultRecord,
        latestRecord,
        Pager(config = PagingConfig(pageSize = 20)) {
          refittedRoom.getExerciseDao().getAllSetRecord(e.exerciseName)
        }.flow,
        currentRecords
      )
    }
    log.i(TAG, "getRecordsForLoadedExercises: records loaded")
    return recordObjects
  }

  companion object {
    private const val TAG = "RoomCacheExerciseRepository"
  }
}
