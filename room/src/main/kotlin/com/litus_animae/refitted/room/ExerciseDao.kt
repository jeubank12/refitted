package com.litus_animae.refitted.room

import androidx.paging.PagingSource
import androidx.room.*
import com.litus_animae.refitted.data.models.DayAndWorkout
import com.litus_animae.refitted.room.entities.RoomExercise
import com.litus_animae.refitted.room.entities.RoomExerciseSet
import com.litus_animae.refitted.room.entities.RoomSetRecord
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ExerciseDao {
  @Query("select distinct step from exerciseset where day = :day and workout = :workout")
  fun getSteps(day: String, workout: String): Flow<List<String>>

  @Query("select distinct step from exerciseset where day = :day and workout = :workout order by primaryStep, superSetStep, alternateStep")
  fun getStepsPages(day: String, workout: String): PagingSource<Int, String>

  @Query("select distinct step from exerciseset where day = :day and workout = :workout")
  suspend fun loadSteps(day: String, workout: String): List<String>

  @Query("select * from exerciseset where day = :day and workout = :workout and step = :step")
  fun getExerciseSet(day: String, workout: String, step: String): Flow<RoomExerciseSet?>

  @Query("select * from exerciseset where day = :day and workout = :workout and step in (:steps) order by step")
  fun getExerciseSets(
    day: String,
    workout: String,
    vararg steps: String
  ): Flow<List<RoomExerciseSet>>

  @Query("select * from exerciseset where day = :day and workout = :workout and step in (:steps) order by step")
  suspend fun loadExerciseSets(
    day: String,
    workout: String,
    vararg steps: String
  ): List<RoomExerciseSet>

  @Query("select * from exerciseset where day = :day and workout = :workout and step = :step")
  suspend fun loadExerciseSet(day: String, workout: String, step: String): RoomExerciseSet?

  @Query("select * from exerciseset where day = :day and workout = :workout order by primaryStep")
  suspend fun loadDayExerciseSets(day: String, workout: String): List<RoomExerciseSet>

  /**
   * Highest [RoomExerciseSet.primaryStep] used on [day], or 0 if the day has no exercises yet -
   * used to pick the next step when a custom exercise is added.
   */
  @Query("select coalesce(max(primaryStep), 0) from exerciseset where day = :day and workout = :workout")
  suspend fun getMaxPrimaryStep(day: String, workout: String): Int

  @Query("delete from exerciseset where day = :day and workout = :workout")
  suspend fun clearDay(day: String, workout: String)

  /**
   * Deletes a single exercise set. Leaves gaps in [RoomExerciseSet.primaryStep] rather than
   * renumbering every other step on the day.
   */
  @Query("delete from exerciseset where day = :day and workout = :workout and step = :step")
  suspend fun deleteExerciseSet(day: String, workout: String, step: String)

  @Query("select * from exercise where exercise_name = :name and exercise_workout = :workout")
  fun getExercise(name: String, workout: String): Flow<RoomExercise?>

  /**
   * Locally-synced exercises whose id starts with [prefix] (pass "$muscleGroup_") - the muscle
   * group is encoded as the id's prefix, see [com.litus_animae.refitted.data.models.Exercise].
   */
  @Query("select * from exercise where exercise_name like :prefix || '%' order by exercise_workout, exercise_name")
  fun getExercisesByMusclePrefix(prefix: String): Flow<List<RoomExercise>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun storeExercise(exercise: RoomExercise)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun storeExerciseSet(exerciseSet: RoomExerciseSet)

  @Transaction
  suspend fun storeExerciseAndSet(exercise: RoomExercise, exerciseSet: RoomExerciseSet) {
    storeExercise(exercise)
    storeExerciseSet(exerciseSet)
  }

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun storeExercises(exercise: List<RoomExercise>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun storeExerciseSets(exerciseSet: List<RoomExerciseSet>)

  @Delete
  suspend fun clearExerciseSets(vararg exerciseSets: RoomExerciseSet)

  /**
   * Moves [from] to [to] ((day, step, workout) is the primary key, so this is a delete +
   * reinsert rather than an update) - used to promote an alternate into a deleted base's step.
   */
  @Transaction
  suspend fun moveExerciseSet(from: RoomExerciseSet, to: RoomExerciseSet) {
    clearExerciseSets(from)
    storeExerciseSet(to)
  }

  @Transaction
  suspend fun storeExercisesAndSets(dayAndWorkout: DayAndWorkout, exercise: List<RoomExercise>, exerciseSet: List<RoomExerciseSet>) {
    val steps = loadSteps(dayAndWorkout.day, dayAndWorkout.workoutId)
    val existingSets = loadExerciseSets(dayAndWorkout.day, dayAndWorkout.workoutId, *steps.toTypedArray())
    clearExerciseSets(*existingSets.toTypedArray())
    storeExercises(exercise)
    storeExerciseSets(exerciseSet)
  }

  /**
   * Gets the records for [targetExercise] and [targetSet] newer than [minDate] in ascending order
   */
  @Query("select * from setrecord where completed > :minDate and exercise = :targetExercise and target_set = :targetSet order by completed")
  fun getSetRecords(
    minDate: Instant,
    targetExercise: String,
    targetSet: String
  ): Flow<List<RoomSetRecord>>

  /**
   * Gets the newest record for the [targetExercise]
   */
  @Query("select * from setrecord where exercise = :targetExercise order by completed desc limit 1")
  fun getLatestSetRecord(targetExercise: String): Flow<RoomSetRecord?>

  /**
   * Gets all recocrds for [targetExercise] in descending chronological order
   */
  @Query("select * from setrecord where exercise = :targetExercise order by completed desc")
  fun getAllSetRecord(targetExercise: String): PagingSource<Int, RoomSetRecord>

  /**
   * Newest [limit] records for [targetExercise], descending - callers reverse for chronology.
   * Unlike [getAllSetRecord], this is a plain list flow so a trend fit can see a stable window
   * of history without depending on how many Paging pages happen to be loaded.
   */
  @Query("select * from setrecord where exercise = :targetExercise order by completed desc limit :limit")
  fun getRecentSetRecords(targetExercise: String, limit: Int): Flow<List<RoomSetRecord>>

  @Insert
  suspend fun storeExerciseRecord(exerciseRecord: RoomSetRecord)

  /**
   * All records logged against target sets belonging to [day] (target_set ids are
   * "$day.$step") - used to derive copy-day targets from what was actually completed.
   */
  @Query("select * from setrecord where workout = :workout and target_set like (:day || '.%') order by completed")
  suspend fun loadDaySetRecords(workout: String, day: String): List<RoomSetRecord>

  @Query(
    "select max(completed) as latest_completion, target_set from setrecord " +
      "where workout = :workout group by target_set"
  )
  fun getDayCompletedSets(workout: String): Flow<List<ExerciseCompletionRecord>>

  @Query("UPDATE Exercise SET exercise_workout = :newName WHERE exercise_workout = :oldName")
  suspend fun renameExerciseWorkout(oldName: String, newName: String)

  @Query("UPDATE exerciseset SET workout = :newName WHERE workout = :oldName")
  suspend fun renameExerciseSetWorkout(oldName: String, newName: String)

  @Query("UPDATE SetRecord SET workout = :newName WHERE workout = :oldName")
  suspend fun renameSetRecordWorkout(oldName: String, newName: String)

  @Query("DELETE FROM Exercise WHERE exercise_workout = :name")
  suspend fun deleteExercisesForWorkout(name: String)

  @Query("DELETE FROM exerciseset WHERE workout = :name")
  suspend fun deleteExerciseSetsForWorkout(name: String)

  @Query("DELETE FROM SetRecord WHERE workout = :name")
  suspend fun deleteSetRecordsForWorkout(name: String)

  data class ExerciseCompletionRecord(
    @ColumnInfo(name = "latest_completion") val latestCompletion: Instant,
    @ColumnInfo(name = "target_set") val dayAndSet: String
  ) {
    @Ignore
    val day = dayAndSet.split("\\.".toRegex(), 2).toTypedArray().getOrElse(0) { "" }
  }
}