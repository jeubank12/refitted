package com.litus_animae.refitted.data.device

import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.data.models.ExerciseSet
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WatchPlanTest {

  private fun exerciseSet(
    step: String,
    name: String,
    sets: Int = 3,
    reps: Int = 10
  ) = ExerciseSet(
    workout = "TestWorkout",
    day = "1",
    step = step,
    name = "Chest_$name",
    note = "",
    reps = reps,
    sets = sets,
    isToFailure = false,
    rest = 60,
    repsUnit = "reps",
    repsRange = 0,
    timeLimit = null,
    timeLimitUnit = null,
    repsSequence = emptyList(),
    exercise = flowOf(null)
  )

  @Nested
  @DisplayName("Flattening resolved sets into a WatchPlan")
  inner class Flattening {
    @Test
    fun `a superset group flattens into consecutive independent exercises, in order`() {
      val resolvedSets = listOf(
        exerciseSet("1.1", "Bench Press"),
        exerciseSet("1.2", "Incline Dumbbell Press"),
        exerciseSet("2", "Tricep Pushdown")
      )

      val plan = buildWatchPlan("TestWorkout", "1", resolvedSets) { 0.0 }

      assertThat(plan.exercises.map { it.name })
        .containsExactly("Bench Press", "Incline Dumbbell Press", "Tricep Pushdown")
        .inOrder()
    }

    @Test
    fun `only the resolved alternate is present, not the whole alternate group`() {
      // The caller (ExerciseViewModel) already resolved "1.a" vs "1.b" down to one ExerciseSet
      // before calling buildWatchPlan - only the chosen alternate should ever reach here.
      val chosenAlternate = exerciseSet("1.b", "Barbell Row")
      val resolvedSets = listOf(chosenAlternate, exerciseSet("2", "Lat Pulldown"))

      val plan = buildWatchPlan("TestWorkout", "1", resolvedSets) { 0.0 }

      assertThat(plan.exercises.map { it.name }).containsExactly("Barbell Row", "Lat Pulldown").inOrder()
    }

    @Test
    fun `workout and day are carried onto the plan`() {
      val plan = buildWatchPlan("PushDay", "3", listOf(exerciseSet("1", "Overhead Press"))) { 0.0 }

      assertThat(plan.workout).isEqualTo("PushDay")
      assertThat(plan.day).isEqualTo("3")
    }

    @Test
    fun `an empty resolved list produces an empty plan`() {
      val plan = buildWatchPlan("TestWorkout", "1", emptyList()) { 0.0 }

      assertThat(plan.exercises).isEmpty()
    }

    @Test
    fun `suggestedWeight comes from the caller-supplied lookup, per set`() {
      val benchPress = exerciseSet("1", "Bench Press")
      val squat = exerciseSet("2", "Squat")

      val plan = buildWatchPlan("TestWorkout", "1", listOf(benchPress, squat)) { set ->
        if (set == benchPress) 135.0 else 225.0
      }

      assertThat(plan.exercises.map { it.suggestedWeight }).containsExactly(135.0, 225.0).inOrder()
    }

    @Test
    fun `sets = -1 for an open challenge set passes through untouched`() {
      val plan = buildWatchPlan("TestWorkout", "1", listOf(exerciseSet("1", "Pushups", sets = -1))) { 0.0 }

      assertThat(plan.exercises.single().sets).isEqualTo(-1)
    }
  }
}
