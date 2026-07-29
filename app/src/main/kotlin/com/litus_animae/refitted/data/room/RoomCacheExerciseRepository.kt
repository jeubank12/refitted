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
import com.litus_animae.refitted.data.models.Exercise
import com.litus_animae.refitted.data.models.ExerciseCompletionRecord
import com.litus_animae.refitted.data.models.ExerciseRecord
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.MuscleGroup
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
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
        return oldItem.id == newItem.id
      }

      // Room hands back a fresh `exercise: Flow<Exercise?>` on every query, so full data-class
      // equality here is nearly always false across a reload even when nothing user-visible
      // changed - that's fine, it just means onChanged fires a bit more than strictly needed,
      // which is harmless (unlike areItemsTheSame returning false, which reads as a delete+
      // insert and makes the item visibly vanish and reappear).
      override fun areContentsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
        return oldItem == newItem
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
          pagingDataDiffer.loadStateFlow
            .map { it.refresh is LoadState.Loading }
            .distinctUntilChanged()
            .collectLatest { isLoading ->
              if (isLoading) {
                // A single-set edit (rest/target steppers) invalidates the same PagingSource
                // and reloads in a few ms - debounce so that blip never reaches the spinner;
                // a genuinely slow load (first open, network refresh) still shows it.
                delay(LOADING_INDICATOR_DEBOUNCE_MILLIS)
                _exercisesAreLoading.emit(true)
              } else {
                _exercisesAreLoading.emit(false)
              }
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

  override suspend fun addCustomExercise(
    workout: String,
    day: String,
    exerciseId: String,
    description: String?
  ) {
    withContext(Dispatchers.IO) {
      val exerciseDao = refittedRoom.getExerciseDao()
      val nextStep = exerciseDao.getMaxPrimaryStep(day, workout) + 1
      // Reusing an existing catalog exercise's id (rather than minting a new one) lets it share
      // one records history across every day/plan it's added to - same convention as
      // admin-authored content (RoomExercise keyed by workout + exercise id).
      log.d(TAG, "adding custom exercise $exerciseId to $workout day $day, step $nextStep")
      // storeExercise REPLACEs the whole row - a blank incoming description would otherwise wipe
      // out a description this id/workout pair already has (e.g. re-adding after a delete).
      val resolvedDescription = description?.takeIf { it.isNotBlank() }
        ?: exerciseDao.getExercise(exerciseId, workout).first()?.description
      exerciseDao.storeExerciseAndSet(
        RoomExercise(workout = workout, id = exerciseId, description = resolvedDescription),
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

  override suspend fun addAlternateExercise(
    workout: String,
    day: String,
    baseStep: String,
    exerciseId: String,
    description: String?
  ) {
    withContext(Dispatchers.IO) {
      val exerciseDao = refittedRoom.getExerciseDao()
      val daySets = exerciseDao.loadDayExerciseSets(day, workout)
      val baseSet = daySets.firstOrNull { it.step == baseStep }
      if (baseSet == null) {
        log.w(TAG, "cannot add alternate: $workout day $day has no step $baseStep")
        return@withContext
      }
      // ExerciseSet.primaryStep only strips a single trailing letter, so alternates past "z" would
      // group as their own instruction rather than joining this one.
      val siblingRegex = "^${Regex.escape(baseStep)}\\.([a-z])$".toRegex()
      val nextLetter = daySets
        .mapNotNull { siblingRegex.find(it.step)?.groupValues?.get(1)?.first() }
        .maxOrNull()?.inc() ?: 'a'
      if (nextLetter > 'z') {
        log.w(TAG, "cannot add alternate: $workout day $day step $baseStep has no free suffix")
        return@withContext
      }
      val step = "$baseStep.$nextLetter"
      log.d(TAG, "adding alternate $exerciseId to $workout day $day, step $step")
      val resolvedDescription = description?.takeIf { it.isNotBlank() }
        ?: exerciseDao.getExercise(exerciseId, workout).first()?.description
      exerciseDao.storeExerciseAndSet(
        RoomExercise(workout = workout, id = exerciseId, description = resolvedDescription),
        // Sort columns are derived from the day-prefixed id, not the step - see RoomExerciseSet.
        baseSet.copy(
          step = step,
          primaryStep = RoomExerciseSet.parsePrimaryStep("$day.$step"),
          superSetStep = RoomExerciseSet.parseSuperSetStep("$day.$step"),
          alternateStep = nextLetter.toString(),
          name = exerciseId,
          // The prescription carries over, but the base's notes are about the base exercise.
          note = ""
        )
      )
    }
  }

  override suspend fun updateCustomExerciseSet(
    workout: String,
    day: String,
    step: String,
    sets: Int,
    reps: Int,
    rest: Int,
    repsRange: Int
  ) {
    // Optimistic: the write below still lands on the `exerciseset` table Room's PagingSource
    // observes, so it'll eventually trigger a reload regardless - patching here means the UI
    // reflects the edit immediately instead of waiting on that round-trip.
    applyOptimisticSetUpdate(workout, day, step, sets, reps, rest, repsRange)
    withContext(Dispatchers.IO) {
      val exerciseDao = refittedRoom.getExerciseDao()
      val existing = exerciseDao.loadExerciseSet(day, workout, step) ?: return@withContext
      exerciseDao.storeExerciseSet(
        existing.copy(sets = sets, reps = reps, rest = rest, repsRange = repsRange)
      )
    }
  }

  private fun applyOptimisticSetUpdate(
    workout: String,
    day: String,
    step: String,
    sets: Int,
    reps: Int,
    rest: Int,
    repsRange: Int
  ) {
    exerciseState.update { current ->
      current.map { set ->
        if (set.workout == workout && set.day == day && set.step == step) {
          set.copy(sets = sets, reps = reps, rest = rest, repsRange = repsRange)
        } else set
      }
    }
  }

  override suspend fun deleteCustomExerciseSet(workout: String, day: String, step: String) {
    // Optimistic, same reasoning as updateCustomExerciseSet - the delete below still lands on
    // the observed table and would eventually reload the list regardless.
    exerciseState.update { current ->
      current.filterNot { it.workout == workout && it.day == day && it.step == step }
    }
    withContext(Dispatchers.IO) {
      refittedRoom.getExerciseDao().deleteExerciseSet(day, workout, step)
    }
  }

  override suspend fun updateCustomExerciseSetNote(workout: String, day: String, step: String, note: String) {
    exerciseState.update { current ->
      current.map { set ->
        if (set.workout == workout && set.day == day && set.step == step) set.copy(note = note)
        else set
      }
    }
    withContext(Dispatchers.IO) {
      val exerciseDao = refittedRoom.getExerciseDao()
      val existing = exerciseDao.loadExerciseSet(day, workout, step) ?: return@withContext
      exerciseDao.storeExerciseSet(existing.copy(note = note))
    }
  }

  override fun exercisesByMuscle(muscle: String): Flow<List<Exercise>> {
    val exerciseDao = refittedRoom.getExerciseDao()
    val prefixQueries = MuscleGroup.prefixesFor(muscle).map { prefix ->
      exerciseDao.getExercisesByMusclePrefix("${prefix}_")
    }
    return combine(prefixQueries) { resultsByPrefix ->
      resultsByPrefix.flatMap { it }
        .distinctBy { it.workout to it.id }
        .map { it.toDomain() }
    }.flowOn(Dispatchers.IO)
  }

  override suspend fun loadRemoteExercisesByMuscle(workout: String, muscle: String): List<Exercise> {
    return withContext(Dispatchers.IO) {
      val exercises = MuscleGroup.prefixesFor(muscle)
        .flatMap { prefix -> networkService.getExercisesByMuscle(workout, prefix) }
        .distinctBy { it.id }
      refittedRoom.getExerciseDao().storeExercises(exercises.map { RoomExercise.fromDomain(it) })
      exercises
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
      // initialLoadSize defaults to pageSize * 3 - too little history for the effort
      // chart's fit to be stable on first composition. A large first page means the
      // trend only ever refines (recency-weighted, so old sessions barely move it) as
      // later pages load, rather than visibly refitting.
      Pager(config = PagingConfig(pageSize = 20, initialLoadSize = 400)) {
        refittedRoom.getExerciseDao().getAllSetRecord(e.exerciseName)
      }.flow.map { it.map { roomRecord -> roomRecord.toDomain() } },
      currentRecords,
      // The DAO call itself is deferred inside this builder - like the Pager above - so
      // building an ExerciseRecord never queries Room unless recentSets is collected.
      flow {
        emitAll(
          refittedRoom.getExerciseDao()
            .getRecentSetRecords(e.exerciseName, SET_RECORD_HISTORY_LIMIT)
            .map { rows -> rows.asReversed().map { it.toDomain() } }
        )
      }.flowOn(Dispatchers.IO)
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
    private const val LOADING_INDICATOR_DEBOUNCE_MILLIS = 250L

    // ~100+ sessions of history; the effort model's recency weighting makes anything
    // beyond that numerically irrelevant, so there's no reason to load more into memory.
    private const val SET_RECORD_HISTORY_LIMIT = 400
  }
}
