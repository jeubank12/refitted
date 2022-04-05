package com.litus_animae.refitted.compose.state

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sign

class Weight(initialValue: Double) {
    private var weight = mutableStateOf(initialValue)
    val value: State<Double> = weight
    fun plus(change: Double) {
        if (change.sign < 0 && weight.value < change.absoluteValue) weight.value = 0.0
        else weight.value += change
    }
    fun set(value: Double) {
        weight.value = max(0.0, value)
    }
}