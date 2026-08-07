package com.litus_animae.refitted.data.device

import com.litus_animae.refitted.data.models.ExerciseSet

/**
 * One flattened exercise as the watch sees it. Index in [WatchPlan.exercises] is its identity.
 */
data class WatchExercise(
  val name: String,
  val sets: Int, // -1 == open/challenge set
  val reps: Int,
  val restSeconds: Int,
  val suggestedWeight: Double,
  val isToFailure: Boolean,
  val repsSequence: List<Int>,
  val timeLimitMillis: Int?
)

/**
 * [ids] is the source `ExerciseSet.id` per entry in [exercises], same order/index - phone-side
 * bookkeeping only, never sent over the wire (`WatchProtocol.encodePlan` only reads [exercises]).
 * It lets the phone resolve an incoming `SET_DONE`'s `exerciseIndex` back into a `SetRecord`
 * without polluting [WatchExercise], which round-trips through the wire format as-is.
 */
data class WatchPlan(
  val workout: String,
  val day: String,
  val exercises: List<WatchExercise>,
  val ids: List<String>
)

/**
 * Maps one already-resolved [ExerciseSet] per instruction into [WatchPlan]. Callers do the
 * alternate resolution and superset ordering before calling this - [resolvedSets] is expected to
 * already be in display order with exactly one entry per instruction (a superset group therefore
 * flattens into consecutive entries here, and each alternate group into the one chosen entry).
 */
fun buildWatchPlan(
  workout: String,
  day: String,
  resolvedSets: List<ExerciseSet>,
  suggestedWeight: (ExerciseSet) -> Double
): WatchPlan = WatchPlan(
  workout = workout,
  day = day,
  exercises = resolvedSets.map { set ->
    WatchExercise(
      name = set.exerciseName,
      sets = set.sets,
      reps = set.reps,
      restSeconds = set.rest,
      suggestedWeight = suggestedWeight(set),
      isToFailure = set.isToFailure,
      repsSequence = set.repsSequence,
      timeLimitMillis = set.timeLimitMilliseconds
    )
  },
  ids = resolvedSets.map { it.id }
)
