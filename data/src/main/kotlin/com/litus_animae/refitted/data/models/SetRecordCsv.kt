package com.litus_animae.refitted.data.models

/**
 * Plain CSV of the raw inputs [com.litus_animae.refitted.data.effort.EffortModel] scores -
 * timestamp, weight, reps - so a chart anomaly can be diagnosed from the exact numbers instead
 * of a screenshot. Exercise is included per row rather than assumed constant since callers may
 * pool records across exercises before exporting.
 */
fun List<SetRecord>.toCsv(): String {
  val header = "completed,exercise,weight,reps"
  val rows = sortedBy { it.completed }
    .joinToString("\n") { "${it.completed},${it.exercise},${it.weight},${it.reps}" }
  return if (rows.isEmpty()) header else "$header\n$rows"
}
