package com.litus_animae.refitted.data.room

import androidx.paging.*
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.models.*
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.progressiveZipWithPrevious
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.inject.Inject

@ExperimentalCoroutinesApi
@FlowPreview
class RoomCacheExerciseRepository @Inject constructor(
  private val refittedRoom: RefittedRoom,
  private val networkService: ExerciseSetNetworkService,
  private val log: LogUtil
) : ExerciseRepository {

  private val currentWorkout = MutableStateFlow("")
  override val workoutRecords = currentWorkout.flatMapLatest {
    refittedRoom.getExerciseDao().getDayCompletedSets(it)
  }

  private val exerciseState: MutableStateFlow<List<ExerciseSet>> = MutableStateFlow(emptyList())
  override val exercises = exerciseState.asStateFlow()

  private val differCallback: DifferCallback = object : DifferCallback {
    override fun onChanged(position: Int, count: Int) {
      if (count > 0) {
        updateExerciseState()
      }
    }

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
  }

  private val pagingDataDiffer: PagingDataDiffer<ExerciseSet> =
    object : PagingDataDiffer<ExerciseSet>(
      differCallback = differCallback
    ) {
      override suspend fun presentNewList(
        previousList: NullPaddedList<ExerciseSet>,
        newList: NullPaddedList<ExerciseSet>,
        lastAccessedIndex: Int,
        onListPresentable: () -> Unit
      ): Int? {
        onListPresentable()
        updateExerciseState()
        return null
      }
    }

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
      ExerciseSetPager(DayAndWorkout(day, workoutId), refittedRoom, networkService, log)
    coroutineScope {

      launch { mediator.pagingData.collectLatest { pagingDataDiffer.collectFrom(it) } }

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
    val tonightMidnight = Date.from(
      LocalDateTime.now().toLocalDate().atStartOfDay().toInstant(ZoneOffset.ofHours(0))
    )
    val recordObjects = loadedExercises.map { e ->
      val defaultReps = when {
        e.repsUnit.isNotBlank() -> 0
        e.sets < 0 -> Integer.min(10, e.reps)
        else -> e.reps
      }
      // TODO appropriate default weights
      val defaultRecord = Record(
        weight = 25.0,
        defaultReps,
        e
      )
      val currentRecords = refittedRoom.getExerciseDao()
        .getSetRecords(tonightMidnight, e.exerciseName)
        .map {
          it.asSequence()
            .progressiveZipWithPrevious { lastRecord: Record?, setRecord ->
              Record(
                setRecord.weight, setRecord.reps, e,
                setRecord.reps + (lastRecord?.cumulativeReps ?: 0), stored = true
              )
            }.toList()
        }
      val latestRecord =
        combine(
          currentRecords,
          refittedRoom.getExerciseDao().getLatestSetRecord(e.exerciseName)
        ) { todayRecords, latestRecord ->
          todayRecords.lastOrNull() ?: latestRecord?.let {
            // TODO appropriate reps?
            Record(it.weight, it.reps, e)
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
