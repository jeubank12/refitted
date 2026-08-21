package com.litus_animae.refitted.ui.compose.state

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import com.litus_animae.refitted.data.models.ExerciseRecord
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.data.models.SetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The set-history drawer's data source. The pager's initialLoadSize is large (see
 * RoomCacheExerciseRepository) so both the grouped list and the effort chart built from it
 * see a full history window on first composition - later pages only refine a causal,
 * recency-weighted fit rather than visibly changing it.
 */
data class SetHistory(
  // A resolved empty page, not emptyFlow() - collectAsLazyPagingItems() never leaves its
  // initial LoadState.Loading refresh state without a first emission, which left the pane
  // spinning forever whenever there were no exercises to point this at.
  val paged: Flow<PagingData<SetRecord>> = flowOf(
    PagingData.empty(
      sourceLoadStates = LoadStates(
        LoadState.NotLoading(true),
        LoadState.NotLoading(true),
        LoadState.NotLoading(true)
      )
    )
  )
)

data class ExerciseSetWithRecord(
  val exerciseSet: ExerciseSet,
  val currentRecord: MutableState<Record>,
  val numCompleted: Int,
  val setRecords: SnapshotStateList<Record>,
  val allSets: Flow<PagingData<SetRecord>>,
  val recentSets: Flow<List<SetRecord>> = emptyFlow()
) {
  fun saveRecordInState(
    savedRecord: Record
  ) {
    if (savedRecord.stored) {
      val recordToSave =
        savedRecord.copy(cumulativeReps = savedRecord.cumulativeReps + savedRecord.reps)
      setRecords.add(recordToSave)
      currentRecord.value = recordToSave.copy(stored = false)
    } else {
      currentRecord.value = savedRecord
    }
  }

  val reps = exerciseSet.reps(numCompleted)

  val exerciseIncomplete = numCompleted < exerciseSet.sets ||
    (exerciseSet.sets < 0 && currentRecord.value.cumulativeReps < reps) ||
    // "challenge" type exercise without a count-of-reps end state
    (exerciseSet.sets < 0 && reps < 0)
}

@Composable
fun recordsByExerciseId(allRecords: List<ExerciseRecord>): Map<String, ExerciseSetWithRecord> {
  // TODO remember through screen rotation, will probably conflict with remember currentRecord below
  val state = remember { mutableStateMapOf<String, ExerciseSetWithRecord>() }
  SideEffect {
    Log.d("recordsByExerciseId", "Processing records")
  }
  allRecords.associateBy { it.targetSet.id }
    .forEach { currentSetEntry ->
      val currentSetRecord = currentSetEntry.value

      val currentSet = currentSetRecord.targetSet
      val todayRecords by currentSetRecord.currentRecords.collectAsState(initial = emptyList())

      // TODO if one already exists in the state, use it instead of this one?
      // hasSyncedInitialRecord must reset together with currentRecord (both plain remember, not
      // rememberSaveable) - currentRecord itself doesn't survive rotation, so if this flag did
      // survive it would report "already synced" while currentRecord sat at defaultRecord,
      // stranding weight/reps at their defaults instead of the last matching set. Plain remember
      // still protects against the original problem this guarded against - stomping an
      // in-progress edit on every latestRecord resubscription replay within the same composition
      // lifetime - since it only re-syncs once per currentSet.id either way.
      val hasSyncedInitialRecord = remember(currentSet.id) { mutableStateOf(false) }
      val currentRecord = remember(currentSet.id) {
        mutableStateOf(currentSetRecord.defaultRecord.copy(stored = false))
      }
      LaunchedEffect(currentSetRecord.latestRecord) {
        currentSetRecord.latestRecord.collect { real ->
          if (!hasSyncedInitialRecord.value) {
            currentRecord.value = real.copy(stored = false)
            hasSyncedInitialRecord.value = true
          }
        }
      }
      val rememberedSetRecords =
        remember(todayRecords) { mutableStateListOf(*todayRecords.toTypedArray()) }
      val setsCompleted by currentSetRecord.currentRecordsCount.collectAsState(
        initial = 0,
        Dispatchers.IO
      )
      // setsCompleted (Room-backed) and rememberedSetRecords (reset from todayRecords whenever
      // that flow re-emits) can each transiently dip below the true count while a just-saved
      // record's write round-trips through Room's invalidation — e.g. an intermediate,
      // not-yet-caught-up emission. numCompletedHighWaterMark remembers the max seen for this
      // exercise so a momentary dip in either source can't un-disable the complete button or
      // revert the "all sets complete" text. The ratcheted value used below is a pure local
      // calculation (so it applies within this same composition, no lag); SideEffect only
      // persists it as next pass's floor, keeping the composable body itself side-effect-free.
      val numCompletedHighWaterMark = remember(currentSet.id) { mutableIntStateOf(0) }
      val rawNumCompleted = maxOf(setsCompleted, rememberedSetRecords.size)
      val effectiveNumCompleted = maxOf(rawNumCompleted, numCompletedHighWaterMark.intValue)
      SideEffect {
        if (effectiveNumCompleted > numCompletedHighWaterMark.intValue) {
          numCompletedHighWaterMark.intValue = effectiveNumCompleted
        }
      }
      val exerciseSetWithRecord = ExerciseSetWithRecord(
        currentSet,
        currentRecord,
        effectiveNumCompleted,
        rememberedSetRecords,
        currentSetRecord.allSets,
        currentSetRecord.recentSets
      )

      state[currentSet.id] = exerciseSetWithRecord
    }
  return state
}
