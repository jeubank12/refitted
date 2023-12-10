package com.litus_animae.refitted.compose.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sign

@Stable
class Weight(initialValue: Double) {
  private var weight = mutableDoubleStateOf(initialValue)
  val value: State<Double> = weight
  fun plus(change: Double) {
    if (change.sign < 0 && weight.doubleValue < change.absoluteValue) weight.doubleValue = 0.0
    else weight.doubleValue += change
  }

  fun set(value: Double) {
    weight.doubleValue = max(0.0, value)
  }

  companion object {
    val Saver = run {
      val weightKey = "weight"
      mapSaver(
        save = { mapOf(weightKey to it.value.value) },
        restore = { Weight(it[weightKey] as Double) }
      )
    }
  }
}