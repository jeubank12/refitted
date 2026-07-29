package com.litus_animae.refitted.ui.compose.exercise.add

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.data.models.Exercise
import com.litus_animae.refitted.data.models.MuscleGroup
import com.litus_animae.refitted.data.models.PlanKind
import com.litus_animae.refitted.data.models.WorkoutPlan

private val muscleGroups = MuscleGroup.displayNames()

/**
 * Target-muscle screen (design 1i): body diagram + chips, both driving the same selection.
 * A separate nav destination from [ExercisePickerList] so system/gesture back steps one
 * screen at a time instead of leaving the whole add-exercise flow.
 */
@Composable
fun MuscleGroupPickerScreen(
  onContinue: (String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  // TODO localize
  title: String = "Add exercise"
) {
  var selected by rememberSaveable { mutableStateOf(muscleGroups.first()) }
  Scaffold(
    contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout),
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(title) },
        windowInsets = AppBarDefaults.topAppBarWindowInsets.union(
          WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
        ),
        backgroundColor = MaterialTheme.colors.primary,
        navigationIcon = {
          // TODO localize
          IconButton(onClick = onClose) { Icon(Icons.Default.Close, "close") }
        }
      )
    }
  ) { contentPadding ->
    Column(modifier.padding(contentPadding).fillMaxSize()) {
      Column(
        Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(16.dp)
      ) {
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
          // TODO localize
          Text("Tap the muscle group to target", style = MaterialTheme.typography.body2)
        }
        Spacer(Modifier.height(14.dp))
        BodyDiagram(selected = selected, onSelect = { selected = it }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          muscleGroups.forEach { muscle ->
            MuscleChip(muscle, selected = muscle == selected, onClick = { selected = muscle })
          }
        }
      }
      Button(
        onClick = { onContinue(selected) },
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
      ) {
        // TODO localize
        Text("Continue — $selected")
      }
    }
  }
}

/** A section of [ExercisePickerList] - one accessible plan, ordered by [kind] then name. */
private data class PickerSection(val name: String, val kind: PlanKind, val isRemoteSource: Boolean)

/**
 * Exercise-list screen (design 1j) - plans the user can pull exercises from, as sections, equipment
 * libraries ([PlanKind.LOCATION]/[PlanKind.EQUIPMENT]) first and admin programs after a divider.
 * Locally-synced matches ([localExercisesByWorkout]) render immediately; other accessible
 * (admin-authored) plans render a "Load exercises" row that triggers an on-demand remote query
 * ([onLoadWorkout] - see ExerciseViewModel.loadRemoteExercises) - nothing is pre-fetched, so the
 * cost of browsing a library sits with whoever taps into it. Custom plans - including the one
 * being edited - only ever show local matches: they're built *from* admin content and have
 * nothing of their own to load remotely. Picking an exercise reuses its exact id so its record
 * history carries over ([onPick]). Every section starts collapsed - [onRefreshWorkouts] is a
 * separate top-bar action re-syncing the plan list itself (new/removed plans), not any one
 * plan's exercises.
 */
@Composable
fun ExercisePickerList(
  muscle: String,
  localExercisesByWorkout: Map<String, List<Exercise>>,
  accessibleWorkouts: List<WorkoutPlan>,
  /** Keyed by (workout, muscle) - a workout's cached rows are only ever for this screen's own muscle. */
  remoteExercisesByWorkout: Map<Pair<String, String>, List<Exercise>>,
  loadingWorkouts: Map<Pair<String, String>, Boolean>,
  onLoadWorkout: (String) -> Unit,
  onPick: (Exercise) -> Unit,
  onBack: () -> Unit,
  /** Re-syncs [accessibleWorkouts] itself (new/removed plans) - separate from a section's own onLoadWorkout, which only refreshes that plan's exercises. */
  onRefreshWorkouts: () -> Unit,
  isRefreshingWorkouts: Boolean,
  modifier: Modifier = Modifier
) {
  val accessibleNames = accessibleWorkouts.map { it.workout }.toSet()
  val sections = (
    accessibleWorkouts.map { PickerSection(it.workout, it.kind, isRemoteSource = true) } +
      localExercisesByWorkout.keys.filter { it !in accessibleNames }
        .map { PickerSection(it, PlanKind.PROGRAM, isRemoteSource = false) }
    )
    .distinctBy { it.name }
    .sortedWith(compareBy({ it.kind.ordinal }, { it.name }))
  val firstProgramIndex = sections.indexOfFirst { it.kind == PlanKind.PROGRAM }
  // Empty by default means every section starts collapsed - with a location/equipment library
  // run plus every program, a fully expanded list is more than fits on screen at once.
  var expandedWorkouts by rememberSaveable { mutableStateOf(setOf<String>()) }
  Scaffold(
    contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout),
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(muscle) },
        windowInsets = AppBarDefaults.topAppBarWindowInsets.union(
          WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
        ),
        backgroundColor = MaterialTheme.colors.primary,
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
        },
        actions = {
          if (isRefreshingWorkouts) {
            CircularProgressIndicator(
              Modifier
                .padding(12.dp)
                .size(20.dp),
              color = MaterialTheme.colors.onPrimary,
              strokeWidth = 2.dp
            )
          } else {
            IconButton(onClick = onRefreshWorkouts) {
              // TODO localize
              Icon(Icons.Default.Refresh, contentDescription = "refresh plan list")
            }
          }
        }
      )
    }
  ) { contentPadding ->
    LazyColumn(Modifier.padding(contentPadding).fillMaxSize()) {
      sections.forEachIndexed { index, section ->
        val workout = section.name
        if (index == firstProgramIndex && index > 0) {
          item(key = "kind-divider") {
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.disabled) {
              // TODO localize
              Text(
                "— programs —",
                Modifier
                  .fillMaxWidth()
                  .padding(start = 10.dp, top = 18.dp, bottom = 2.dp),
                style = MaterialTheme.typography.overline
              )
            }
          }
        }
        // Custom plans have no remote catalog of their own (see the KDoc above) - only ever
        // appear here from localExercisesByWorkout, so they get no refresh affordance.
        val isRemoteSource = section.isRemoteSource
        val loading = loadingWorkouts[workout to muscle] == true
        val hasFetched = remoteExercisesByWorkout.containsKey(workout to muscle)
        // Merge rather than local ?: remote - a workout opened locally for one day is only
        // ever partially synced, so its cached rows and a fresh remote fetch both contribute.
        val exercises = (localExercisesByWorkout[workout].orEmpty() + remoteExercisesByWorkout[workout to muscle].orEmpty())
          .distinctBy { it.id }
          .sortedBy { it.name ?: it.id }
        // A remote section's count isn't meaningful until it's been fetched at least once (or a
        // local cache already answers it) - showing 0 before that would read as "nothing here"
        // rather than "not checked yet".
        val knowsCount = !isRemoteSource || hasFetched || exercises.isNotEmpty()

        val isCollapsed = workout !in expandedWorkouts
        item(key = "header:$workout") {
          Row(
            Modifier
              .fillMaxWidth()
              .padding(start = 10.dp, end = 6.dp, top = 14.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              Modifier
                .clickable(onClickLabel = if (isCollapsed) "expand $workout" else "collapse $workout") {
                  expandedWorkouts = if (isCollapsed) expandedWorkouts + workout else expandedWorkouts - workout
                }
                .padding(end = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              val rotation by animateFloatAsState(
                if (isCollapsed) -90f else 0f,
                label = "collapseChevron"
              )
              Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                  .size(28.dp)
                  .padding(4.dp)
                  .rotate(rotation)
              )
              Text(workout, style = MaterialTheme.typography.overline)
              if (knowsCount) {
                Spacer(Modifier.width(6.dp))
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                  Text(exercises.size.toString(), style = MaterialTheme.typography.overline)
                }
              }
            }
            if (isRemoteSource) {
              if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
              } else {
                IconButton(
                  onClick = { onLoadWorkout(workout) },
                  modifier = Modifier.size(28.dp)
                ) {
                  // TODO localize
                  Icon(
                    Icons.Default.Refresh,
                    contentDescription = "refresh $workout",
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }
        if (isCollapsed) return@forEachIndexed
        when {
          exercises.isEmpty() && loading -> item(key = "loading:$workout") {
            Row(
              Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
              horizontalArrangement = Arrangement.Center
            ) {
              CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
          }

          exercises.isEmpty() && !hasFetched -> item(key = "load:$workout") {
            Row(
              Modifier
                .fillMaxWidth()
                .clickable { onLoadWorkout(workout) }
                .padding(start = 10.dp, end = 6.dp, top = 15.dp, bottom = 15.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              // TODO localize
              Text("Refresh to load", style = MaterialTheme.typography.button)
            }
          }

          exercises.isEmpty() -> item(key = "empty:$workout") {
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.disabled) {
              // TODO localize
              Text(
                "No $muscle exercises",
                Modifier
                  .fillMaxWidth()
                  .padding(start = 10.dp, bottom = 10.dp),
                style = MaterialTheme.typography.body2
              )
            }
          }

          else -> items(exercises, key = { "exercise:${it.workout}:${it.id}" }) { exercise ->
            Row(
              Modifier
                .fillMaxWidth()
                .clickable { onPick(exercise) }
                .padding(start = 10.dp, end = 6.dp, top = 15.dp, bottom = 15.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(exercise.name ?: exercise.id, style = MaterialTheme.typography.button)
              Icon(Icons.Default.Add, "add ${exercise.name}", tint = MaterialTheme.colors.primary)
            }
            Divider()
          }
        }
      }
    }
  }
}

@Composable
private fun MuscleChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
    contentColor = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
    border = if (selected) null else BorderStroke(
      1.dp,
      MaterialTheme.colors.onSurface.copy(alpha = 0.23f)
    ),
    modifier = Modifier.clickable(onClick = onClick)
  ) {
    Text(label, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.body2)
  }
}

// Front/back body diagram (design 1i) - a stand-in anatomy figure, not tied to real muscle
// imagery. Each tappable region shares selection state with the chips below it; positions are
// lifted straight from the design mockup's 150x330 canvas (px treated as dp).
private data class BodyPart(
  val muscle: String?,
  val x: Int,
  val y: Int,
  val w: Int,
  val h: Int,
  val shape: Shape
)

private val roundedPart = RoundedCornerShape(40)

private val frontBodyParts = listOf(
  BodyPart(null, 57, 0, 36, 36, CircleShape),
  BodyPart(null, 68, 35, 14, 10, RoundedCornerShape(2.dp)),
  BodyPart("Shoulders", 29, 45, 26, 16, roundedPart),
  BodyPart("Shoulders", 95, 45, 26, 16, roundedPart),
  BodyPart("Chest", 45, 47, 60, 36, RoundedCornerShape(10.dp)),
  BodyPart("Biceps", 26, 64, 15, 46, roundedPart),
  BodyPart("Biceps", 109, 64, 15, 46, roundedPart),
  BodyPart("Core", 49, 87, 52, 54, RoundedCornerShape(10.dp)),
  // Forearms dropped: no admin program has ever stored an exercise under that prefix.
  BodyPart(null, 24, 114, 12, 44, roundedPart),
  BodyPart(null, 114, 114, 12, 44, roundedPart),
  BodyPart(null, 47, 145, 56, 22, roundedPart),
  BodyPart("Quads", 47, 171, 24, 78, RoundedCornerShape(12.dp)),
  BodyPart("Quads", 79, 171, 24, 78, RoundedCornerShape(12.dp)),
  BodyPart("Calves", 50, 255, 18, 62, roundedPart),
  BodyPart("Calves", 82, 255, 18, 62, roundedPart)
)

private val backBodyParts = listOf(
  BodyPart(null, 57, 0, 36, 36, CircleShape),
  BodyPart(null, 68, 35, 14, 10, RoundedCornerShape(2.dp)),
  BodyPart("Traps", 51, 42, 48, 18, roundedPart),
  BodyPart("Shoulders", 29, 48, 20, 14, roundedPart),
  BodyPart("Shoulders", 101, 48, 20, 14, roundedPart),
  BodyPart("Lats", 45, 63, 60, 52, RoundedCornerShape(10.dp)),
  BodyPart("Triceps", 26, 64, 15, 46, roundedPart),
  BodyPart("Triceps", 109, 64, 15, 46, roundedPart),
  BodyPart("Lower back", 55, 118, 40, 24, roundedPart),
  BodyPart("Glutes", 47, 145, 56, 26, RoundedCornerShape(12.dp)),
  BodyPart("Hamstrings", 47, 175, 24, 72, RoundedCornerShape(12.dp)),
  BodyPart("Hamstrings", 79, 175, 24, 72, RoundedCornerShape(12.dp)),
  BodyPart("Calves", 50, 252, 18, 62, roundedPart),
  BodyPart("Calves", 82, 252, 18, 62, roundedPart)
)

@Composable
private fun BodyDiagram(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      BodyFigure(frontBodyParts, selected, onSelect)
      Spacer(Modifier.height(8.dp))
      // TODO localize
      Text("FRONT", style = MaterialTheme.typography.overline)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      BodyFigure(backBodyParts, selected, onSelect)
      Spacer(Modifier.height(8.dp))
      // TODO localize
      Text("BACK", style = MaterialTheme.typography.overline)
    }
  }
}

@Composable
private fun BodyFigure(parts: List<BodyPart>, selected: String, onSelect: (String) -> Unit) {
  Box(Modifier.size(150.dp, 330.dp)) {
    parts.forEach { part ->
      val isSelected = part.muscle == selected
      Box(
        Modifier
          .offset(x = part.x.dp, y = part.y.dp)
          .size(part.w.dp, part.h.dp)
          .let { base ->
            if (part.muscle != null) {
              base.clickable(onClickLabel = part.muscle, onClick = { onSelect(part.muscle) })
            } else base
          }
          .background(
            if (isSelected) MaterialTheme.colors.primary
            else MaterialTheme.colors.onSurface.copy(alpha = 0.14f),
            part.shape
          )
          .let { base ->
            if (isSelected) {
              base.border(2.dp, MaterialTheme.colors.primary.copy(alpha = 0.4f), part.shape)
            } else base
          }
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun PreviewMuscleGroupPicker() {
  MaterialTheme {
    MuscleGroupPickerScreen(onContinue = {}, onClose = {})
  }
}

@Preview(showBackground = true)
@Composable
private fun PreviewExercisePickerList() {
  MaterialTheme {
    ExercisePickerList(
      muscle = "Chest",
      localExercisesByWorkout = mapOf(
        "Push Pull Legs" to listOf(
          Exercise("Push Pull Legs", "Chest_Barbell Bench Press"),
          Exercise("Push Pull Legs", "Chest_Push-Up")
        )
      ),
      accessibleWorkouts = listOf(
        WorkoutPlan("Planet Fitness", kind = PlanKind.LOCATION),
        WorkoutPlan("Push Pull Legs"),
        WorkoutPlan("Full Body")
      ),
      // "Planet Fitness" sorts first as a LOCATION and has never been fetched, so it falls through
      // to "Refresh to load". "Push Pull Legs" shows the merge of its cached local row plus a
      // remote fetch already in hand. "Full Body" is a PROGRAM, sorted after the "— programs —"
      // divider, and also unfetched.
      remoteExercisesByWorkout = mapOf(
        ("Push Pull Legs" to "Chest") to listOf(Exercise("Push Pull Legs", "Chest_Incline Dumbbell Press"))
      ),
      loadingWorkouts = emptyMap(),
      onLoadWorkout = {},
      onPick = {},
      onBack = {},
      onRefreshWorkouts = {},
      isRefreshingWorkouts = false
    )
  }
}
