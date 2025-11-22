package com.litus_animae.refitted.compose.exercise

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.litus_animae.refitted.HiltTestActivity
import com.litus_animae.refitted.data.TestDataBuilder
import com.litus_animae.refitted.room.RefittedRoomProvider
import com.litus_animae.refitted.ui.compose.exercise.Exercise
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Integration test for exercise pagination stability.
 *
 * This test verifies that when a user saves a set record while viewing the second exercise,
 * the cascading data reload through the Flow chain doesn't reset the pagination state
 * back to the first exercise.
 *
 * This is a critical behavior that depends on object equality in the data flow:
 * RoomCacheExerciseRepository.records -> ExerciseViewModel.records ->
 * ExerciseView.setRecords -> ExerciseSetView
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ExercisePaginationStabilityTest {

  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

  @Inject
  lateinit var roomProvider: RefittedRoomProvider

  @Before
  fun setup() {
    hiltRule.inject()

    // Populate the in-memory database with test data
    runBlocking {
      val db = roomProvider.refittedRoom
      val exerciseDao = db.getExerciseDao()
      val workoutPlanDao = db.getWorkoutPlanDao()

      // Create 3 test exercises for pagination
      val exerciseSets = TestDataBuilder.createTestExerciseSets(count = 3)
      val exercises = TestDataBuilder.createTestExercises(count = 3)
      val workoutPlan = TestDataBuilder.createTestWorkoutPlan()

      // Insert test data
      exercises.forEach { exerciseDao.storeExercise(it) }
      exerciseSets.forEach { exerciseDao.storeExerciseSet(it) }
      workoutPlanDao.insertAll(listOf(workoutPlan))
    }
  }

  @Test
  fun savingRecordOnSecondExercise_doesNotResetToFirstExercise() {
    // Set up the Exercise composable - it will load the ViewModel via Hilt
    composeTestRule.setContent {
      Exercise(
        day = TestDataBuilder.TEST_DAY,
        workoutId = TestDataBuilder.TEST_WORKOUT
      )
    }

    // Wait for initial composition and verify first exercise is displayed
    // Exercise names are "TestExercise_TestExercise1" -> displays "TestExercise1"
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      composeTestRule.onAllNodesWithText("TestExercise1", substring = true).fetchSemanticsNodes()
        .isNotEmpty()
    }

    // Navigate to the second exercise using the ">" button
    composeTestRule.onNodeWithText(">")
      .assertIsEnabled()
      .performClick()

    // Wait for navigation to complete
    composeTestRule.waitForIdle()

    // Verify we're now on the second exercise (displays "TestExercise2")
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      composeTestRule.onAllNodesWithText("TestExercise2", substring = true).fetchSemanticsNodes()
        .isNotEmpty()
    }

    // Save a set record by clicking the complete button
    // This will trigger: onSave -> model.saveExercise -> database write ->
    // Flow cascade -> UI update (and start 1-second rest timer)
    composeTestRule.onNodeWithText("Complete", substring = true)
      .performClick()

    // Wait for the database write and Flow cascade to complete
    composeTestRule.waitForIdle()

    // CRITICAL ASSERTION: Verify we're STILL on the second exercise
    // If the pagination state was reset, we would be back on TestExercise1
    composeTestRule.onNodeWithText("TestExercise2", substring = true).assertIsDisplayed()
  }

  @Test
  fun savingRecordOnLastExercise_doesNotResetToFirstExercise() {
    // Set up the Exercise composable
    composeTestRule.setContent {
      Exercise(
        day = TestDataBuilder.TEST_DAY,
        workoutId = TestDataBuilder.TEST_WORKOUT
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      composeTestRule.onAllNodesWithText("TestExercise1", substring = true).fetchSemanticsNodes()
        .isNotEmpty()
    }

    // Navigate to the third (last) exercise
    repeat(2) {
      composeTestRule.onNodeWithText(">")
        .assertIsEnabled()
        .performClick()
      composeTestRule.waitForIdle()
    }

    // Verify we're on the third exercise
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      composeTestRule.onAllNodesWithText("TestExercise3", substring = true).fetchSemanticsNodes()
        .isNotEmpty()
    }

    // Save a set record
    composeTestRule.onNodeWithText("Complete", substring = true)
      .performClick()

    composeTestRule.waitForIdle()

    // Verify we're STILL on the third exercise
    composeTestRule.onNodeWithText("TestExercise3", substring = true).assertIsDisplayed()
  }

  @Test
  fun savingMultipleRecordsOnSecondExercise_maintainsPagination() {
    // Set up the Exercise composable
    composeTestRule.setContent {
      Exercise(
        day = TestDataBuilder.TEST_DAY,
        workoutId = TestDataBuilder.TEST_WORKOUT
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      composeTestRule.onAllNodesWithText("TestExercise1", substring = true).fetchSemanticsNodes()
        .isNotEmpty()
    }

    // Navigate to second exercise
    composeTestRule.onNodeWithText(">")
      .assertIsEnabled()
      .performClick()
    composeTestRule.waitForIdle()

    // Verify on second exercise
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      composeTestRule.onAllNodesWithText("TestExercise2", substring = true).fetchSemanticsNodes()
        .isNotEmpty()
    }

    // Save first set
    composeTestRule.onNodeWithText("Complete", substring = true)
      .performClick()
    composeTestRule.waitForIdle()

    // Verify still on second exercise
    composeTestRule.onNodeWithText("TestExercise2", substring = true).assertIsDisplayed()

    // Save second set
    composeTestRule.onNodeWithText("Complete", substring = true)
      .performClick()
    composeTestRule.waitForIdle()

    // Verify STILL on second exercise after multiple saves
    composeTestRule.onNodeWithText("TestExercise2", substring = true).assertIsDisplayed()
  }
}
