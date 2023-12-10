package com.litus_animae.refitted.compose.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sign

@Stable
class Repetitions(initialValue: Int) {
  private var reps = mutableIntStateOf(max(0, initialValue))
  val value: State<Int> = reps
  fun plus(change: Int) {
    if (change.sign < 0 && reps.intValue < change.absoluteValue) reps.intValue = 0
    else reps.intValue += change
  }
  fun set(value: Int) {
    reps.intValue = max(value, 0)
  }

  companion object {
    val Saver = run {
      val repsKey = "reps"
      mapSaver(
        save = { mapOf(repsKey to it.value.value) },
        restore = { Repetitions(it[repsKey] as Int) }
      )
    }
  }
}