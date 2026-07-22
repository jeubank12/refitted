package com.litus_animae.refitted.data.room

import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.data.models.DayAndWorkout
import com.litus_animae.refitted.data.models.ExerciseCompletionRecord
import com.litus_animae.refitted.data.models.ExerciseRecord
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.room.RefittedRoomProvider
import com.litus_animae.refitted.room.entities.RoomExercise
import com.litus_animae.refitted.room.entities.RoomExerciseSet
import com.litus_animae.refitted.room.entities.RoomSetRecord
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.progressiveZipWithPrevious
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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
    refittedRoom.getExerciseDao().getDayCompletedSets(it).map { daoRecords ->
      daoRecords.map { daoRecord ->
        ExerciseCompletionRecord(
          latestCompletion = daoRecord.latestCompletion,
          dayAndSet = daoRecord.dayAndSet
        )
      }
    }
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
    val isCustom = withContext(Dispatchers.IO) {
      refittedRoom.getWorkoutPlanDao().getByName(workoutId)?.isCustom == true
    }
    val pagingData = if (isCustom) {
      log.i(TAG, "loadExercises: $workoutId is a custom plan, paginating from Room only")
      customExercisePagingData(day, workoutId)
    } else {
      ExerciseSetPager(DayAndWorkout(day, workoutId), roomProvider, networkService, log).pagingData
    }
    coroutineScope {

      launch { pagingData.collectLatest { pagingDataDiffer.submitData(it) } }

      launch {
        pagingData.collectLatest {
          pagingDataDiffer.loadStateFlow.collect {
            _exercisesAreLoading.emit(it.refresh is LoadState.Loading)
          }
        }
      }
    }
  }

  // Custom plans have no network-authored content, so this paginates straight from Room with no
  // RemoteMediator - a pull-to-refresh can then never reach the network and wipe locally-added
  // exercises via ExerciseDao.storeExercisesAndSets.
  private fun customExercisePagingData(day: String, workoutId: String): Flow<PagingData<ExerciseSet>> {
    val exerciseDao = refittedRoom.getExerciseDao()
    return Pager(PagingConfig(20)) {
      exerciseDao.getStepsPages(day, workoutId)
    }.flow.mapLatest { pagingData ->
      pagingData.map { step ->
        val roomSet = exerciseDao.loadExerciseSet(day, workoutId, step)!!
        buildExerciseSet(exerciseDao, roomSet)
      }
    }.flowOn(Dispatchers.IO)
  }

  override val records =
    exercises.map { loadedExercises ->
      // FIXME this should be a real timezone?
      val tonightMidnight = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.ofHours(0))

      getRecordsForLoadedExercises(tonightMidnight, loadedExercises)
    }


  override suspend fun storeSetRecord(record: SetRecord) {
    withContext(Dispatchers.IO) {
      log.d(TAG, "storing set record")
      refittedRoom.getExerciseDao().storeExerciseRecord(RoomSetRecord.fromDomain(record))
      log.d(TAG, "stored set record")
    }
  }

  override fun loadWorkoutRecords(workoutId: String) {
    currentWorkout.value = workoutId
  }

  override suspend fun addCustomExercise(workout: String, day: String, exerciseName: String) {
    withContext(Dispatchers.IO) {
      val exerciseDao = refittedRoom.getExerciseDao()
      val nextStep = exerciseDao.getMaxPrimaryStep(day, workout) + 1
      // Reusing the exercise name as its Room id lets the same custom exercise, added on
      // different days of the same plan, share one records history - same convention as
      // admin-authored content (RoomExercise keyed by workout + exercise id).
      val exerciseId = "custom_$exerciseName"
      log.d(TAG, "adding custom exercise $exerciseId to $workout day $day, step $nextStep")
      exerciseDao.storeExerciseAndSet(
        RoomExercise(workout = workout, id = exerciseId),
        RoomExerciseSet(
          workout = workout,
          day = day,
          step = nextStep.toString(),
          primaryStep = nextStep,
          superSetStep = null,
          alternateStep = null,
          name = exerciseId,
          note = "",
          reps = -1,
          sets = -1,
          isToFailure = false,
          rest = 90,
          repsUnit = "",
          repsRange = 0,
          timeLimit = null,
          timeLimitUnit = null,
          repsSequence = emptyList()
        )
      )
    }
  }

  fun getRecordsForLoadedExercises(
    sinceDate: Instant,
    loadedExercises: List<ExerciseSet>
  ): List<ExerciseRecord> {
    log.i(
      TAG,
      "getRecordsForLoadedExercises: detected ${loadedExercises.size} new exercises, loading records"
    )
    val recordObjects = loadedExercises.map { e ->
      buildExerciseRecord(e, sinceDate)
    }
    log.i(TAG, "getRecordsForLoadedExercises: records loaded")
    return recordObjects
  }

  fun buildExerciseRecord(
    e: ExerciseSet,
    sinceDate: Instant
  ): ExerciseRecord {
    val defaultRecord = buildDefaultRecordForExerciseSet(e)
    val currentRecords = getCurrentRecords(sinceDate, e)
    val latestRecord =
      combine(
        currentRecords,
        refittedRoom.getExerciseDao().getLatestSetRecord(e.exerciseName).map { it?.toDomain() }
      ) { todayRecords, latestRecord ->
        todayRecords.lastOrNull() ?: latestRecord?.let {
          buildNewDayUnstoredRecord(e, it)
        }
      }.mapNotNull { it }
    return ExerciseRecord(
      e,
      defaultRecord,
      latestRecord,
      Pager(config = PagingConfig(pageSize = 20)) {
        refittedRoom.getExerciseDao().getAllSetRecord(e.exerciseName)
      }.flow.map { it.map { roomRecord -> roomRecord.toDomain() } },
      currentRecords
    )
  }

  fun buildNewDayUnstoredRecord(
    e: ExerciseSet,
    record: SetRecord
  ): Record {
    // here we know that the exercise has not been performed today
    // reps should not necessarily be blindly copied from the last set
    val reps = when {
      e.repsUnit.isNotBlank() && e.id == record.targetSet -> record.reps
      e.repsUnit.isNotBlank() -> 10
      e.reps(0) < 0 -> record.reps
      e.sets < 0 -> min(10, e.reps(0))
      else -> e.reps(0)
    }
    return Record(record.weight, reps, e, record.completed)
  }

  fun buildDefaultRecordForExerciseSet(e: ExerciseSet): Record {
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
    return defaultRecord
  }

  fun getCurrentRecords(
    sinceDate: Instant,
    targetExerciseSet: ExerciseSet
  ): Flow<List<Record>> = refittedRoom.getExerciseDao()
    .getSetRecords(sinceDate, targetExerciseSet.exerciseName, targetExerciseSet.id)
    .map { roomRecords ->
      roomRecords.asSequence()
        .map { it.toDomain() }
        .progressiveZipWithPrevious { lastRecord: Record?, setRecord ->
          Record(
            setRecord.weight, setRecord.reps, targetExerciseSet, setRecord.completed,
            setRecord.reps + (lastRecord?.cumulativeReps ?: 0),
            stored = true
          )
        }.toList()
    }

  companion object {
    private const val TAG = "RoomCacheExerciseRepository"
  }
}
