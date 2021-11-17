package com.litus_animae.refitted.compose

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WorkoutDay(val workoutId: String, val day: String) : Parcelable
