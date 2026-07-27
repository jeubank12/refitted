package com.litus_animae.refitted.data

import com.litus_animae.refitted.data.models.Exercise
import com.litus_animae.refitted.data.models.ExerciseCompletionRecord
import com.litus_animae.refitted.data.models.ExerciseRecord
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.SetRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
   * Adds an exercise to a custom plan's day, as an open (no set limit) set. [exerciseId] should
   * be an existing catalog exercise's id to share its record history, or a fresh
   * "{muscleGroup}_{name}" id for a user-authored exercise. [description] carries over the source
   * exercise's instructions, if any - a null/blank value never overwrites an existing description
   * already stored for this id under [workout].
   */
  suspend fun addCustomExercise(workout: String, day: String, exerciseId: String, description: String? = null)

  /**
   * Updates a custom (BYO) exercise set's prescription - target [sets], [reps], and [rest] - in
   * place, keyed by [workout]/[day]/[step]. No-op if the set doesn't exist (e.g. admin content).
   */
  suspend fun updateCustomExerciseSet(workout: String, day: String, step: String, sets: Int, reps: Int, rest: Int)

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
