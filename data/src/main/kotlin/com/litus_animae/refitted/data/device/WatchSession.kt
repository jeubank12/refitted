package com.litus_animae.refitted.data.device

import com.litus_animae.refitted.data.models.SetRecord
import java.time.Instant

/**
 * Phone-side bookkeeping for an active watch session. [startInstant] is the phone's own clock at
 * the moment [WatchProtocol.Plan] was sent - the watch never sends a wall-clock timestamp, only
 * elapsed milliseconds since then, so the phone stays the sole time authority.
 */
data class WatchSessionState(
  val workout: String,
  val startInstant: Instant,
  val plan: WatchPlan
)

/**
 * Resolves a decoded [WatchProtocol.SetDone] against the session it belongs to. Returns null for
 * an [WatchProtocol.SetDone.exerciseIndex] outside the session's plan - stale data from a session
 * that has since ended - rather than throwing.
 *
 * Because [WatchSessionState.startInstant] is fixed per session and `elapsedMs` is whatever the
 * watch sent, replaying the same completion always produces the same [SetRecord.completed], which
 * is the dedup key `RoomSetRecord`'s primary key relies on.
 */
fun WatchProtocol.SetDone.toSetRecord(session: WatchSessionState): SetRecord? {
  val exercise = session.plan.exercises.getOrNull(exerciseIndex) ?: return null
  val id = session.plan.ids.getOrNull(exerciseIndex) ?: return null
  return SetRecord(
    weight = weightCenti / 100.0,
    reps = reps,
    workout = session.workout,
    targetSet = id,
    completed = session.startInstant.plusMillis(elapsedMs),
    exercise = exercise.name
  )
}
