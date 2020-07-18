package com.litus_animae.refitted.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.time.Instant
import java.util.*

@Entity(primaryKeys = ["exercise", "completed"])
class SetRecord {
    var weight = 0.0
    var reps = 0
    var workout: String? = null
    @ColumnInfo(name = "target_set")
    var targetSet: String? = null
    var completed: Date
    var exercise = ""

    constructor(targetSet: ExerciseSet, weight: Double, reps: Int) {
        this.weight = weight
        this.reps = reps
        completed = Date.from(Instant.now())
        exercise = targetSet.exerciseName
        this.targetSet = targetSet.id
        workout = targetSet.workout
    }

    constructor() {
        completed = Date.from(Instant.now())
    }

    override fun equals(other: Any?): Boolean {
        val record = other as SetRecord?
        return record != null &&
            record.workout == workout &&
            record.targetSet == targetSet &&
            record.reps == reps &&
            record.weight == weight &&
            record.completed == completed
    }
}