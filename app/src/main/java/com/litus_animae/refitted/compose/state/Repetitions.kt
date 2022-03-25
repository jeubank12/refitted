package com.litus_animae.refitted.compose.state

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sign

class Repetitions(initialValue: Int) {
  private var reps = mutableStateOf(max(0, initialValue))
  val value: State<Int> = reps
  fun plus(change: Int) {
    if (change.sign < 0 && reps.value < change.absoluteValue) reps.value = 0
    else reps.value += change
  }
}