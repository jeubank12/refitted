package com.litus_animae.refitted.data.effort

import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.data.models.SetRecord
import java.time.Instant

/**
 * A single completed set, reduced to the fields the effort model needs.
 * Decouples [com.litus_animae.refitted.data.effort.EffortModel] from the
 * persistence-facing [SetRecord] and the in-session [Record] shapes.
 */
data class EffortSet(
  val completed: Instant,
  val weight: Double,
  val reps: Int
)

fun SetRecord.toEffortSet(): EffortSet = EffortSet(completed, weight, reps)

fun Record.toEffortSet(): EffortSet = EffortSet(completed, weight, reps)
