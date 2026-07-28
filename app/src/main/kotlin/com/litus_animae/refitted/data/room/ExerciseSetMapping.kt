package com.litus_animae.refitted.data.room

import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.room.ExerciseDao
import com.litus_animae.refitted.room.entities.RoomExerciseSet
import kotlinx.coroutines.flow.map

/**
 * Builds the domain [ExerciseSet], resolving its [Exercise][com.litus_animae.refitted.data.models.Exercise]
 * as a live Room-backed flow rather than [RoomExerciseSet.toDomain]'s `flowOf(null)`. Shared by
 * [ExerciseSetPager] (network-backed days) and [RoomCacheExerciseRepository] (custom, local-only days).
 */
internal fun buildExerciseSet(exerciseDao: ExerciseDao, roomSet: RoomExerciseSet): ExerciseSet =
  ExerciseSet(
    workout = roomSet.workout,
    day = roomSet.day,
    step = roomSet.step,
    name = roomSet.name,
    note = roomSet.note,
    reps = roomSet.reps,
    sets = roomSet.sets,
    isToFailure = roomSet.isToFailure,
    rest = roomSet.rest,
    repsUnit = roomSet.repsUnit,
    repsRange = roomSet.repsRange,
    timeLimit = roomSet.timeLimit,
    timeLimitUnit = roomSet.timeLimitUnit,
    repsSequence = roomSet.repsSequence,
    exercise = exerciseDao.getExercise(roomSet.name, roomSet.workout).map { it?.toDomain() }
  )
