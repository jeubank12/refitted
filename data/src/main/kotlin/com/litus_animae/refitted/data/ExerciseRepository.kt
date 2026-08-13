package com.litus_animae.refitted.data

import com.litus_animae.refitted.data.models.Exercise
import com.litus_animae.refitted.data.models.ExerciseCompletionRecord
import com.litus_animae.refitted.data.models.ExerciseRecord
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.SetRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/**
 * Repository interface for exercise-related operations.
 * Abstracts data access for exercises, sets, and records.
 */
interface ExerciseRepository {
  suspend fun loadExercises(day: String, workoutId: String)
  fun refreshExercises()
  suspend fun storeSetRecord(record: SetRecord)
  fun loadWorkoutRecords(workoutId: String)

  /**
   * Updates a previously-logged set's [weight] and [reps] in place, keyed by [exercise]/[completed]
   * - SetRecord's composite identity (see RoomSetRecord's primary key). The completion timestamp
   * itself is not editable here: it's part of the persisted primary key, and changing it would need
   * delete+reinsert semantics rather than a plain update. No-op if the record doesn't exist.
   */
  suspend fun updateSetRecord(exercise: String, completed: Instant, weight: Double, reps: Int)

  /**
   * Deletes a single logged set, keyed by [exercise]/[completed]. No-op if the record doesn't exist.
   */
  suspend fun deleteSetRecord(exercise: String, completed: Instant)

  /**
   * Adds an exercise to a custom plan's day, as an open (no set limit) set. [exerciseId] should
   * be an existing catalog exercise's id to share its record history, or a fresh
   * "{muscleGroup}_{name}" id for a user-authored exercise. [description] carries over the source
   * exercise's instructions, if any - a null/blank value never overwrites an existing description
   * already stored for this id under [workout].
   */
  suspend fun addCustomExercise(workout: String, day: String, exerciseId: String, description: String? = null)

  /**
   * Adds [exerciseId] as an alternate of the set at [baseStep] - an [ExerciseSet.primaryStep], so
   * either a plain step ("3") or a superset member ("2.3"). The new set takes the next free "a".."z"
   * suffix (e.g. "3.b") and inherits the base set's prescription, since an alternate substitutes for
   * the same slot. [description] behaves as in [addCustomExercise]. Throws when [baseStep] has no
   * set on this day, or when all 26 suffixes are taken.
   */
  suspend fun addAlternateExercise(
    workout: String,
    day: String,
    baseStep: String,
    exerciseId: String,
    description: String? = null
  )

  /**
   * Updates a custom (BYO) exercise set's prescription - target [sets], [reps], [rest], and
   * [repsRange] (an offset above [reps]; e.g. reps=10/repsRange=2 prescribes "10-12") - in place,
   * keyed by [workout]/[day]/[step]. No-op if the set doesn't exist (e.g. admin content).
   */
  suspend fun updateCustomExerciseSet(
    workout: String,
    day: String,
    step: String,
    sets: Int,
    reps: Int,
    rest: Int,
    repsRange: Int
  )

  /**
   * Deletes a single custom exercise set, keyed by [workout]/[day]/[step]. Leaves the shared
   * `Exercise` row and any completed `SetRecord` history in place - re-adding the same exercise
   * later recovers its history. No-op if the set doesn't exist.
   */
  suspend fun deleteCustomExerciseSet(workout: String, day: String, step: String)

  /**
   * Updates a custom (BYO) exercise set's free-text instructions in place, keyed by
   * [workout]/[day]/[step]. No-op if the set doesn't exist.
   */
  suspend fun updateCustomExerciseSetNote(workout: String, day: String, step: String, note: String)
  val exercises: Flow<List<ExerciseSet>>
  val exercisesAreLoading: StateFlow<Boolean>
  val records: Flow<List<ExerciseRecord>>
  val workoutRecords: Flow<List<ExerciseCompletionRecord>>

  /**
   * Locally-synced exercises for [muscle] (the id's muscle-group prefix), across every workout
   * that has been opened locally.
   */
  fun exercisesByMuscle(muscle: String): Flow<List<Exercise>>

  /**
   * On-demand remote lookup of [workout]'s exercises for [muscle] - not persisted locally, purely
   * for browsing in the add-exercise picker.
   */
  suspend fun loadRemoteExercisesByMuscle(workout: String, muscle: String): List<Exercise>
}
