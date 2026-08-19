package com.litus_animae.refitted.ui.compose.exercise.add

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
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
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.litus_animae.refitted.data.models.Exercise
import com.litus_animae.refitted.data.models.MuscleGroup
import com.litus_animae.refitted.data.models.PlanKind
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.compose.AuthButton
import com.litus_animae.refitted.ui.compose.util.appBarColors

private val muscleGroups = MuscleGroup.displayNames()

/**
 * Target-muscle picker content (design 1i): body diagram + chips, both driving the same
 * selection. Applies instantly - there's no confirm step, the caller closes the panel itself
 * once [onSelect] fires.
 */
@Composable
fun MuscleGroupPicker(
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier
      .verticalScroll(rememberScrollState())
      .padding(16.dp)
  ) {
    // TODO localize
    Text(
      "Tap the muscle group to target",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(14.dp))
    BodyDiagram(selected = selected, onSelect = onSelect, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      muscleGroups.forEach { muscle ->
        MuscleChip(muscle, selected = muscle == selected, onClick = { onSelect(muscle) })
      }
    }
  }
}

/** A section of [AddExerciseList] - one accessible plan, ordered by [kind] then name. */
private data class PickerSection(val name: String, val kind: PlanKind, val isRemoteSource: Boolean)

/**
 * Exercise-list content (design 1j) - plans the user can pull exercises from, as sections, equipment
 * libraries ([PlanKind.LOCATION]/[PlanKind.EQUIPMENT]) first and admin programs after a divider.
 * Locally-synced matches ([localExercisesByWorkout]) render immediately; other accessible
 * (admin-authored) plans render a "Load exercises" row that triggers an on-demand remote query
 * ([onLoadWorkout] - see ExerciseViewModel.loadRemoteExercises) - nothing is pre-fetched, so the
 * cost of browsing a library sits with whoever taps into it. Custom plans - including the one
 * being edited - only ever show local matches: they're built *from* admin content and have
 * nothing of their own to load remotely. Picking an exercise reuses its exact id so its record
 * history carries over ([onPick]). Every section starts collapsed - [onRefreshWorkouts] is a
 * separate top-bar action re-syncing the plan list itself (new/removed plans), not any one
 * plan's exercises. The current [muscle] is shown as a tappable header ([onMuscleTap]) rather
 * than a plain title - it's a live filter now, not something picked on a screen before this one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseList(
  title: String,
  muscle: String,
  onMuscleTap: () -> Unit,
  localExercisesByWorkout: Map<String, List<Exercise>>,
  accessibleWorkouts: List<WorkoutPlan>,
  /** Keyed by (workout, muscle) - a workout's cached rows are only ever for this screen's own muscle. */
  remoteExercisesByWorkout: Map<Pair<String, String>, List<Exercise>>,
  loadingWorkouts: Map<Pair<String, String>, Boolean>,
  onLoadWorkout: (String) -> Unit,
  onPick: (Exercise) -> Unit,
  onClose: () -> Unit,
  /** Re-syncs [accessibleWorkouts] itself (new/removed plans) - separate from a section's own onLoadWorkout, which only refreshes that plan's exercises. */
  onRefreshWorkouts: () -> Unit,
  isRefreshingWorkouts: Boolean,
  /** Null when signed out - shows a "sign in for more exercises" CTA below the list. */
  authedEmail: String?,
  onSignInSuccess: (GetCredentialResponse) -> Unit,
  onSignInFailure: (GetCredentialException) -> Unit,
  webClientId: String,
  /** False when this is hosted as a small centered docked pane rather than a full-screen sheet - it never reaches a physical screen edge, so it shouldn't reserve display-cutout space. */
  edgeToEdge: Boolean = true,
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
    contentWindowInsets = WindowInsets.navigationBars.let {
      if (edgeToEdge) it.union(WindowInsets.displayCutout) else it
    },
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(title) },
        windowInsets = if (edgeToEdge) {
          WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
        } else {
          WindowInsets(0, 0, 0, 0)
        },
        colors = appBarColors(),
        navigationIcon = {
          // There's no picker screen left to step back to - this closes the whole add-exercise
          // sheet, so it reads as "close" rather than "back".
          // TODO localize
          IconButton(onClick = onClose) { Icon(Icons.Default.Close, "close") }
        },
        actions = {
          if (isRefreshingWorkouts) {
            CircularProgressIndicator(
              Modifier
                .padding(12.dp)
                .size(20.dp),
              color = MaterialTheme.colorScheme.onPrimary,
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
    },
    bottomBar = {
      if (authedEmail == null) {
        AuthButton(
          Modifier
            .windowInsetsPadding(
              WindowInsets.navigationBars.let {
                if (edgeToEdge) it.union(WindowInsets.displayCutout) else it
              }
            )
            .padding(16.dp),
          handleAuthSuccess = onSignInSuccess,
          handleAuthFailure = onSignInFailure,
          handleDeAuth = {},
          authedEmail = null,
          webClientId = webClientId,
          ctaText = "Sign in for more exercises"
        )
      }
    }
  ) { contentPadding ->
    LazyColumn(Modifier.padding(contentPadding).fillMaxSize()) {
      item(key = "muscle-header") {
        SelectedMuscleHeader(muscle, onClick = onMuscleTap)
      }
      sections.forEachIndexed { index, section ->
        val workout = section.name
        if (index == firstProgramIndex && index > 0) {
          item(key = "kind-divider") {
            // TODO localize
            Text(
              "— programs —",
              Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 18.dp, bottom = 2.dp),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.outline
            )
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
              Text(workout, style = MaterialTheme.typography.labelSmall)
              if (knowsCount) {
                Spacer(Modifier.width(6.dp))
                Text(
                  exercises.size.toString(),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
              Text("Refresh to load", style = MaterialTheme.typography.labelLarge)
            }
          }

          exercises.isEmpty() -> item(key = "empty:$workout") {
            // TODO localize
            Text(
              "No $muscle exercises",
              Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, bottom = 10.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.outline
            )
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
              Text(exercise.name ?: exercise.id, style = MaterialTheme.typography.labelLarge)
              Icon(Icons.Default.Add, "add ${exercise.name}", tint = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
          }
        }
      }
    }
  }
}

@Composable
private fun SelectedMuscleHeader(muscle: String, onClick: () -> Unit) {
  Column {
    Row(
      Modifier
        .fillMaxWidth()
        .clickable(onClickLabel = "change target muscle", onClick = onClick)
        .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        // TODO localize
        Text(
          "Target muscle",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(muscle, style = MaterialTheme.typography.titleMedium)
      }
      Icon(Icons.Default.ExpandMore, contentDescription = null)
    }
    HorizontalDivider()
  }
}

/**
 * Combines [AddExerciseList] with an [AnimatedVisibility] overlay of [MuscleGroupPicker] -
 * a plain `Box` overlay rather than a second nested `ModalBottomSheet`, so its swipe/anchor math
 * never runs against an already-clamped outer sheet, and it doesn't need a second Popup window
 * stacked on the outer sheet's own (see ui/CLAUDE.md's guidance on hosting overlays in an
 * unclipped ancestor instead of layering more sheet/Popup machinery). System/gesture
 * back closes just the picker first via the [BackHandler] below, which - because it only composes
 * while [pickingMuscle] is true, itself nested inside the outer sheet's own content - registers
 * (and so takes priority) after that sheet's own built-in back handling.
 */
@Composable
fun AddExercisePanel(
  title: String,
  muscle: String,
  onMuscleSelected: (String) -> Unit,
  localExercisesByWorkout: Map<String, List<Exercise>>,
  accessibleWorkouts: List<WorkoutPlan>,
  remoteExercisesByWorkout: Map<Pair<String, String>, List<Exercise>>,
  loadingWorkouts: Map<Pair<String, String>, Boolean>,
  onLoadWorkout: (String) -> Unit,
  onPick: (Exercise) -> Unit,
  onClose: () -> Unit,
  onRefreshWorkouts: () -> Unit,
  isRefreshingWorkouts: Boolean,
  authedEmail: String?,
  onSignInSuccess: (GetCredentialResponse) -> Unit,
  onSignInFailure: (GetCredentialException) -> Unit,
  webClientId: String,
  /** False when this is hosted as a small centered docked pane rather than a full-screen sheet - it never reaches a physical screen edge, so it shouldn't reserve display-cutout space. */
  edgeToEdge: Boolean = true,
  modifier: Modifier = Modifier
) {
  var pickingMuscle by rememberSaveable { mutableStateOf(false) }

  BackHandler(enabled = pickingMuscle) { pickingMuscle = false }

  Box(modifier.fillMaxSize()) {
    AddExerciseList(
      title = title,
      muscle = muscle,
      onMuscleTap = { pickingMuscle = true },
      localExercisesByWorkout = localExercisesByWorkout,
      accessibleWorkouts = accessibleWorkouts,
      remoteExercisesByWorkout = remoteExercisesByWorkout,
      loadingWorkouts = loadingWorkouts,
      onLoadWorkout = onLoadWorkout,
      onPick = onPick,
      onClose = onClose,
      onRefreshWorkouts = onRefreshWorkouts,
      isRefreshingWorkouts = isRefreshingWorkouts,
      authedEmail = authedEmail,
      onSignInSuccess = onSignInSuccess,
      onSignInFailure = onSignInFailure,
      webClientId = webClientId,
      edgeToEdge = edgeToEdge
    )
    AnimatedVisibility(
      visible = pickingMuscle,
      enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
      exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 })
    ) {
      Surface(Modifier.fillMaxSize()) {
        Column {
          Row(
            Modifier
              .fillMaxWidth()
              .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // TODO localize
            Text("Target muscle", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { pickingMuscle = false }) {
              Icon(Icons.Default.Close, "close")
            }
          }
          MuscleGroupPicker(
            selected = muscle,
            onSelect = {
              onMuscleSelected(it)
              pickingMuscle = false
            },
            modifier = Modifier.weight(1f)
          )
        }
      }
    }
  }
}

@Composable
private fun MuscleChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    border = if (selected) null else BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.onSurface.copy(alpha = 0.23f)
    ),
    modifier = Modifier.clickable(onClick = onClick)
  ) {
    Text(label, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium)
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
      Text("FRONT", style = MaterialTheme.typography.labelSmall)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      BodyFigure(backBodyParts, selected, onSelect)
      Spacer(Modifier.height(8.dp))
      // TODO localize
      Text("BACK", style = MaterialTheme.typography.labelSmall)
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
            if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
            part.shape
          )
          .let { base ->
            if (isSelected) {
              base.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), part.shape)
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
    MuscleGroupPicker(selected = "Chest", onSelect = {})
  }
}

@Preview(showBackground = true)
@Composable
private fun PreviewAddExerciseList() {
  MaterialTheme {
    AddExerciseList(
      title = "Add exercise",
      muscle = "Chest",
      onMuscleTap = {},
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
      onClose = {},
      onRefreshWorkouts = {},
      isRefreshingWorkouts = false,
      authedEmail = null,
      onSignInSuccess = {},
      onSignInFailure = {},
      webClientId = ""
    )
  }
}
