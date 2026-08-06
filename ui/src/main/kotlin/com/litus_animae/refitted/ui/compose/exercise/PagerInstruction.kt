package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalAbsoluteElevation
import androidx.compose.material.LocalElevationOverlay
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arrow.core.nonEmptyListOf
import com.litus_animae.refitted.ui.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.ui.compose.util.Theme
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sign

private const val minRotation = 1f
private const val maxRotation = 3f
private const val maxTranslation = 15f

/** Safe wrapped access — returns 0 if the list is empty (guards against divide-by-zero during initial composition). */
private fun List<Float>.wrapped(index: Int) = if (isEmpty()) 0f else get(index % size)

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun PagerExerciseInstructions(
  instructions: List<ExerciseViewModel.ExerciseInstruction>,
  pagerState: PagerState,
  alternateIndex: Int?,
  /** Source of `alternateIndex` and any plan-wide alternate name overrides for the card's chip. */
  workoutPlan: WorkoutPlan? = null,
  onAlternateChange: (Int) -> Unit = {},
  /**
   * Records keyed by exercise-set ID. Each card looks up its own numCompleted so all
   * pre-composed pages have correct data without waiting for the parent to re-pass it.
   */
  setRecords: Map<String, ExerciseSetWithRecord> = emptyMap(),
  editing: Boolean = false,
  onDeleteExercise: (ExerciseSet) -> Unit = {},
  onEditNote: (exerciseSet: ExerciseSet, note: String) -> Unit = { _, _ -> },
) {
  val pageRotations = remember(instructions.size) {
    val rotation = maxRotation - minRotation
    instructions.indices
      .map { _ ->
        val r = (minRotation + Math.random().toFloat() * rotation)
        if (Math.random() < 0.5) -r else r
      }
      .toList()
  }
  val xTransforms = remember(instructions.size) {
    instructions.indices
      .map { _ ->
        val t = Math.random().toFloat() * maxTranslation
        if (Math.random() < 0.5) -t else t
      }
      .toList()
  }
  val yTransforms = remember(instructions.size) {
    instructions.indices
      .map { _ ->
        val t = Math.random().toFloat() * maxTranslation
        if (Math.random() < 0.5) -t else t
      }
      .toList()
  }

  val scope = rememberCoroutineScope()

  Column(Modifier.padding(top = 8.dp)) {
    Box(
      Modifier
        .weight(5f, fill = true)
        .fillMaxWidth()
    ) {
      // Deck layer: every card is composed exactly once and pinned at its jittered rest
      // position, so all cards are already rendered before a swipe starts. A card is
      // hidden here only while the pager layer above is animating it.
      Box(
        Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        val currentPage = pagerState.currentPage
        instructions.forEachIndexed { idx, instruction ->
          key(idx) {
            // Same continuous focus the pager layer computes, keyed the same way (distance
            // from the settled scroll position) — the pager hands a card off to the deck
            // mid-focus (at distance -0.5, pageFocus 0.5) as it slides out to the left, so
            // without this the card's elevation snapped from 3.5dp to a hardcoded 1dp right
            // at the handoff.
            val pageFocus by remember(idx) {
              derivedStateOf {
                val scroll = pagerState.currentPage + pagerState.currentPageOffsetFraction
                (1f - (idx - scroll).absoluteValue).coerceIn(0f, 1f)
              }
            }
            CardContent(
              instruction, alternateIndex, setRecords,
              // The deck's own copy of a card is never the authoritative, focused render (the
              // pager owns that for the settled page) — never worth animating a swap here.
              isActivePage = false,
              pageFocus = pageFocus,
              workoutPlan = workoutPlan,
              onAlternateChange = onAlternateChange,
              editing = editing,
              onDeleteExercise = onDeleteExercise,
              onEditNote = onEditNote,
              modifier = Modifier
                .fillMaxSize()
                // Upcoming cards stack above previously completed ones
                .zIndex(
                  if (idx >= currentPage) (currentPage - idx).toFloat()
                  else (idx - currentPage - instructions.size).toFloat()
                )
                .graphicsLayer {
                  val scroll = pagerState.currentPage + pagerState.currentPageOffsetFraction
                  val distance = idx - scroll
                  val settleFraction = ceil(scroll) - scroll
                  val spread = size.width + 2 * 16.dp.toPx()
                  if (distance > -1f && distance <= -0.5f) {
                    // Far half of this card's trip to or from the bottom of the deck.
                    // Rendered here, not in the pager layer, so it slides underneath
                    // the deck like a physical card being tucked under or pulled out.
                    alpha = 1f
                    val absDistance = distance.absoluteValue
                    val deckFraction = 2f * absDistance - 1f
                    translationX = -spread * min(absDistance, 1f - absDistance) +
                      xTransforms.wrapped(idx) * deckFraction
                    translationY = yTransforms.wrapped(idx) * deckFraction
                    rotationZ = pageRotations.wrapped(idx) * deckFraction
                  } else {
                    alpha = if (distance.absoluteValue < 1f) 0f else 1f
                    // The whole deck slides under the incoming top card like a physical
                    // deck: it rides the same swing as the positive-side transition
                    // card, returning to center as the swipe settles.
                    translationX = xTransforms.wrapped(idx) +
                      spread * min(settleFraction, 1f - settleFraction)
                    translationY = yTransforms.wrapped(idx)
                    rotationZ = pageRotations.wrapped(idx)
                  }
                }
            )
          }
        }
      }

      HorizontalPager(
        pagerState,
        Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      ) { page ->
        val pagesFromCurrent = page - pagerState.currentPage
        // Continuous distance of this page from front-and-center, in pages — the same
        // measure the graphicsLayer block below uses for position. Driving elevation from
        // this instead of the isActivePage boolean means it tracks the drag itself rather
        // than jumping into a tween at the halfway point where currentPage flips.
        val pageFocus by remember(page) {
          derivedStateOf {
            val distance = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
            (1f - distance.absoluteValue).coerceIn(0f, 1f)
          }
        }
        CardContent(
          instructions.getOrNull(page), alternateIndex, setRecords,
          // Only the settled, front-and-center page should ever escape into the unclipped
          // popup — anything mid-swipe or off to the side would just be a phantom animation
          // bleeding through on top of whatever the user is actually looking at.
          isActivePage = pagesFromCurrent == 0,
          pageFocus = pageFocus,
          workoutPlan = workoutPlan,
          onAlternateChange = onAlternateChange,
          editing = editing,
          onDeleteExercise = onDeleteExercise,
          onEditNote = onEditNote,
          modifier = Modifier
            .fillMaxSize()
            // The card leaving the top of the deck stays above the one being revealed or
            // pulled out; the swap happens at the halfway flip, where both cards are
            // clear of each other
            .zIndex(
              if (pagesFromCurrent == 0) 0f
              else if (pagesFromCurrent > 0) -2f
              else -3f
            )
            .graphicsLayer {
              // Continuous distance of this page from front-and-center, in pages.
              // Derived only from draw-time state so nothing jumps when currentPage
              // flips at the halfway threshold.
              val distance =
                (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
              val absDistance = distance.absoluteValue
              if (absDistance >= 1f || distance <= -0.5f) {
                // At rest in the deck, or on the far half of a trip to or from the
                // bottom of the deck — the deck layer underneath renders this card
                alpha = 0f
              } else {
                alpha = 1f
                // Swing out to the side and back, cancelling the pager's own slot
                // offset. At the halfway point the two moving cards clear each other by
                // the content padding on both sides, keeping the z-order swap invisible.
                val spread = size.width + 2 * 16.dp.toPx()
                translationX =
                  distance.sign * spread * min(absDistance, 1f - absDistance) -
                    distance * size.width
                // Blend the deck jitter in as the card settles toward its rest position
                val deckFraction = (2f * absDistance - 1f).coerceAtLeast(0f)
                rotationZ = pageRotations.wrapped(page) * deckFraction
                translationX += xTransforms.wrapped(page) * deckFraction
                translationY = yTransforms.wrapped(page) * deckFraction
              }
            }
        )
      }
    }

    // ← page dots → navigation row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
        enabled = pagerState.currentPage > 0
      ) {
        Icon(Icons.Default.ChevronLeft, contentDescription = "previous exercise")
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        repeat(instructions.size) { idx ->
          val isActive = idx == pagerState.currentPage
          Box(
            Modifier
              .size(if (isActive) 10.dp else 6.dp)
              .clip(CircleShape)
              .background(
                if (isActive) MaterialTheme.colors.primary
                else MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
              )
          )
        }
      }

      IconButton(
        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
        enabled = pagerState.currentPage < instructions.size - 1
      ) {
        Icon(Icons.Default.ChevronRight, contentDescription = "next exercise")
      }
    }

    val instruction = instructions.getOrNull(pagerState.currentPage)
    // Keyed on the instruction's stable identity, not the instruction object itself - every edit
    // anywhere rebuilds the whole instructions list into fresh ExerciseInstruction/ExerciseSet
    // objects (each carrying a newly-queried `exercise: Flow<Exercise?>` field that's never equal
    // across reloads), so keying on `instruction` would recreate this flow - and reset the
    // collected state to null for a frame - on every unrelated edit, not just this card's own.
    val exerciseSetFlow = remember(instruction?.sets?.head?.id, alternateIndex) {
      instruction?.set(alternateIndex)
    }
    val exerciseSet by exerciseSetFlow
      ?.collectAsStateWithLifecycle(initialValue = null)
      ?: remember { mutableStateOf(null) }

    ExerciseTimer(timeLimitMilliseconds = exerciseSet?.timeLimitMilliseconds)
  }
}

@OptIn(FlowPreview::class)
@Composable
private fun CardContent(
  instruction: ExerciseViewModel.ExerciseInstruction?,
  alternateIndex: Int?,
  setRecords: Map<String, ExerciseSetWithRecord>,
  isActivePage: Boolean,
  /** Continuous 0..1 distance-from-center, in pages — 0 for deck cards, which never focus. */
  pageFocus: Float = 0f,
  workoutPlan: WorkoutPlan? = null,
  onAlternateChange: (Int) -> Unit = {},
  modifier: Modifier = Modifier,
  editing: Boolean = false,
  onDeleteExercise: (ExerciseSet) -> Unit = {},
  onEditNote: (exerciseSet: ExerciseSet, note: String) -> Unit = { _, _ -> },
) {
  // Keyed on the instruction's stable identity, not the instruction object itself - every edit
  // anywhere rebuilds the whole instructions list into fresh ExerciseInstruction/ExerciseSet
  // objects (each carrying a newly-queried `exercise: Flow<Exercise?>` field that's never equal
  // across reloads), so keying on `instruction` would recreate this flow - and reset the
  // collected state to null for a frame - on every unrelated edit, not just this card's own.
  val exerciseSetFlow = remember(instruction?.sets?.head?.id, alternateIndex) {
    instruction?.set(alternateIndex)
  }
  val exerciseSet by exerciseSetFlow
    ?.collectAsStateWithLifecycle(initialValue = null)
    ?: remember { mutableStateOf(null) }

  // This card's slot may sit inside HorizontalPager, which clips page content to its own
  // bounds — so a card mid-swap can't just translate past that edge, it gets torn off at a
  // straight line instead of sliding cleanly offscreen. Captured here so the popup below can
  // size/position an unclipped copy to match exactly.
  var cardSize by remember { mutableStateOf(IntSize.Zero) }

  // The very first exercise set to land in this card slot should just appear — only a genuine
  // alternate switch (a real set replacing another real set) gets the swap motion below. Set
  // from transitionSpec (which runs once per actual content-key change, using AnimatedContent's
  // own initialState) rather than tracked by hand — a hand-rolled "previous value" var read at
  // the top of this function gets re-evaluated on every unrelated recomposition mid-transition
  // (setRecords updates constantly), which can flip it after the fact.
  var isAlternateSwap by remember { mutableStateOf(false) }

  // An alternate switch swaps the exercise shown in this same card slot — animate the whole
  // card (surface and content together) all the way off toward the upper right and pull the
  // new one in from the same corner, so it reads as a distinct gesture from the deck's
  // left-right page swipe rather than an abrupt content pop.
  AnimatedContent(
    targetState = exerciseSet,
    modifier = modifier,
    contentKey = { it?.id },
    transitionSpec = {
      isAlternateSwap = initialState != null
      EnterTransition.None togetherWith ExitTransition.None
    },
    label = "alternateSwap"
  ) { targetSet ->
    val swapProgress by transition.animateFloat(
      label = "alternateSwapProgress",
      transitionSpec = { tween(380, easing = FastOutSlowInEasing) }
    ) { state ->
      if (!isActivePage || !isAlternateSwap || state == EnterExitState.Visible) 0f else 1f
    }
    val isSwapping = isActivePage && isAlternateSwap && transition.isRunning

    // Popup visibility is staggered a frame behind the inline card's own hide/unhide so
    // there's always an overlap, never a gap: the popup mounts before the inline card hides,
    // and the inline card unhides before the popup tears down.
    var popupVisible by remember { mutableStateOf(false) }
    var inlineHidden by remember { mutableStateOf(false) }
    LaunchedEffect(isSwapping) {
      if (isSwapping) {
        popupVisible = true
        withFrameNanos {}
        inlineHidden = true
      } else {
        inlineHidden = false
        withFrameNanos {}
        popupVisible = false
      }
    }

    @Composable
    fun SwapCard(cardModifier: Modifier) {
      // Driven straight off pageFocus (the page's continuous distance from center) rather
      // than a boolean + tween — tracks the drag itself instead of jumping into an animation
      // at the halfway point where isActivePage flips. Passed to Card's own elevation param,
      // not just a graphicsLayer shadow, so the tonal surface color moves with it too.
      //
      // Known issue: this card's rounded corners render square for the duration of the
      // NavHost fade transition when first navigating in from the calendar, self-correcting
      // once the fade settles — a Compose framework bug, not caused by the elevation logic
      // here. See https://issuetracker.google.com/issues/375496210.
      val elevation = lerp(1.dp, 6.dp, pageFocus)
      Card(
        cardModifier.graphicsLayer {
          // Clear the card's own full bounds plus a margin, so it's completely offscreen at
          // the extremes rather than just peeking out from behind the corner.
          translationX = swapProgress * (size.width + 32.dp.toPx())
          translationY = -swapProgress * (size.height + 32.dp.toPx())
          rotationZ = swapProgress * 18f
        },
        elevation = elevation,
      ) {
        CompositionLocalProvider(
          LocalOverscrollFactory provides null
        ) {
          // Each card self-serves its own progress from the records map — no reflow on swipe
          ExerciseInstructions(
            targetSet,
            setRecords[targetSet?.id]?.numCompleted ?: 0,
            instruction = instruction,
            workoutPlan = workoutPlan,
            onAlternateChange = onAlternateChange,
            editing = editing,
            onDeleteExercise = onDeleteExercise,
            onEditNote = onEditNote
          )
        }
      }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { cardSize = it }) {
      // Hidden (not removed — the pager still needs this slot's layout/gesture area) while
      // the unclipped popup copy below is doing the actual swap motion.
      SwapCard(Modifier.fillMaxSize().alpha(if (inlineHidden) 0f else 1f))
    }

    if (popupVisible && cardSize != IntSize.Zero) {
      Popup(
        alignment = Alignment.TopStart,
        properties = PopupProperties(focusable = false, clippingEnabled = false)
      ) {
        val density = LocalDensity.current
        SwapCard(
          Modifier.size(
            with(density) { cardSize.width.toDp() },
            with(density) { cardSize.height.toDp() }
          )
        )
      }
    }
  }
}

@OptIn(FlowPreview::class)
@Preview(showBackground = true, widthDp = 400, heightDp = 400)
@Preview(showBackground = true, widthDp = 400, heightDp = 500)
@Preview(showBackground = true, widthDp = 300, heightDp = 300)
@Composable
private fun PreviewPagerExerciseInstructions(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
  MaterialTheme(Theme.darkColors) {
    val pagerState = rememberPagerState { 4 }
    PagerExerciseInstructions(
      instructions = IntArray(4) { 1 }.asList().map { _ ->
        ExerciseViewModel.ExerciseInstruction(
          nonEmptyListOf(exerciseSet),
          null,
          MutableStateFlow(0)
        )
      },
      pagerState,
      null,
    )
  }
}

@OptIn(FlowPreview::class)
@Preview(showBackground = true, widthDp = 400, heightDp = 400)
@Preview(showBackground = true, widthDp = 300, heightDp = 300)
@Composable
private fun PreviewPagerExerciseInstructionsWithAlternate() {
  MaterialTheme(Theme.darkColors) {
    val pagerState = rememberPagerState { 1 }
    val alternateSet = exampleExerciseSet.copy(step = "1.a", name = "B_Dumbbell Press")
    PagerExerciseInstructions(
      instructions = listOf(
        ExerciseViewModel.ExerciseInstruction(
          nonEmptyListOf(exampleExerciseSet, alternateSet),
          null,
          MutableStateFlow(0)
        )
      ),
      pagerState,
      null,
      editing = true,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
private fun ExerciseInstructions(
  exerciseSet: ExerciseSet?,
  /** Defaults to 0 so the progress line always occupies space from first paint — no layout jump. */
  numCompleted: Int = 0,
  instruction: ExerciseViewModel.ExerciseInstruction? = null,
  workoutPlan: WorkoutPlan? = null,
  onAlternateChange: (Int) -> Unit = {},
  editing: Boolean = false,
  onDeleteExercise: (ExerciseSet) -> Unit = {},
  onEditNote: (exerciseSet: ExerciseSet, note: String) -> Unit = { _, _ -> },
) {
  val cardColor = LocalElevationOverlay.current?.apply(MaterialTheme.colors.surface, LocalAbsoluteElevation.current)
    ?: MaterialTheme.colors.surface
  // Hoisted (rather than collected only inside the LazyColumn item below) so the edit dialog can
  // show the same description without a second subscription.
  val exercise by (exerciseSet?.exercise ?: emptyFlow()).collectAsStateWithLifecycle(null)
  // The band anchored above the pinned counter, bottom to top: solidHeight is fully
  // opaque card background (guarantees the counter never sits on visible text, since a
  // linear fade alone only reaches full opacity at its very last pixel), then fadeHeight
  // transitions back to transparent, then transparentHeight passes content through as-is.
  val transparentHeight = 20.dp
  val fadeHeight = 64.dp
  val solidHeight = 20.dp
  val totalHeight = transparentHeight + fadeHeight + solidHeight
  val fadeStartFraction = transparentHeight / totalHeight
  val fadeEndFraction = (transparentHeight + fadeHeight) / totalHeight

  Box(Modifier.fillMaxSize()) {
    LazyColumn(
      Modifier
        .fillMaxSize()
        .padding(top = 8.dp, start = 12.dp, end = 12.dp),
      // Extra bottom padding so scrollable content clears the pinned set counter
      contentPadding = PaddingValues(bottom = 40.dp)
    ) {
      stickyHeader {
        Surface(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
          Column {
            Text(
              text = exerciseSet?.exerciseName ?: "",
              style = MaterialTheme.typography.h4
            )
            if (exerciseSet != null) {
              val prescriptionText = buildPrescriptionText(exerciseSet)
              Text(
                text = prescriptionText,
                style = MaterialTheme.typography.subtitle2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
              )
            }
          }
        }
      }
      item {
        if (exerciseSet != null) {
          Text(exercise?.description ?: "", Modifier.padding(bottom = 5.dp))
        }
      }
      item {
        Text(exerciseSet?.note ?: "")
      }
    }

    // Fades scrolling text into the card background before it reaches the pinned set
    // counter below, so the counter always sits on a clean backdrop regardless of
    // where the content is scrolled to.
    Box(
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(totalHeight)
        .background(
          Brush.verticalGradient(
            0f to cardColor.copy(alpha = 0f),
            fadeStartFraction to cardColor.copy(alpha = 0f),
            fadeEndFraction to cardColor,
            1f to cardColor
          )
        )
    )

    // Alternate chip and set progress share the bottom row so the counter only shifts off
    // center when a chip is actually present, rather than reserving space for one that isn't.
    Row(
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (instruction != null) {
        AlternateChip(
          instruction = instruction,
          workoutPlan = workoutPlan,
          onAlternateChange = onAlternateChange,
          modifier = Modifier.weight(1f, fill = false)
        )
      }
      if (exerciseSet != null && exerciseSet.sets > 0) {
        val allSetsComplete = numCompleted >= exerciseSet.sets
        Box(Modifier.weight(1f)) {
          Text(
            text = if (allSetsComplete) "All sets complete" else "Set ${numCompleted + 1} of ${exerciseSet.sets}",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.align(Alignment.Center)
          )
        }
      }
    }

    // Anchored to the Box, not inside the LazyColumn, so it stays put regardless of scroll.
    if (editing && exerciseSet != null) {
      var confirmingDelete by remember(exerciseSet.id) { mutableStateOf(false) }
      var editingNote by remember(exerciseSet.id) { mutableStateOf(false) }
      Row(modifier = Modifier.align(Alignment.TopEnd)) {
        IconButton(onClick = { editingNote = true }) {
          // TODO localize
          Icon(Icons.Default.Edit, contentDescription = "edit ${exerciseSet.exerciseName} instructions")
        }
        IconButton(onClick = { confirmingDelete = true }) {
          // TODO localize
          Icon(Icons.Default.Close, contentDescription = "remove ${exerciseSet.exerciseName}")
        }
      }
      if (confirmingDelete) {
        AlertDialog(
          onDismissRequest = { confirmingDelete = false },
          // TODO localize
          title = { Text("Remove ${exerciseSet.exerciseName}?") },
          text = { Text("This removes it from today's plan. Your past completed sets are kept.") },
          confirmButton = {
            Button(onClick = {
              confirmingDelete = false
              onDeleteExercise(exerciseSet)
            }) { Text("Remove") }
          },
          dismissButton = {
            Button(onClick = { confirmingDelete = false }) { Text("Cancel") }
          }
        )
      }
      if (editingNote) {
        EditInstructionDialog(
          exerciseName = exerciseSet.exerciseName,
          description = exercise?.description,
          initialNote = exerciseSet.note,
          onDismissRequest = { editingNote = false },
          onSave = { note ->
            editingNote = false
            onEditNote(exerciseSet, note)
          }
        )
      }
    }
  }
}

/** Formats a human-readable prescription string from an [ExerciseSet]. */
private fun buildPrescriptionText(exerciseSet: ExerciseSet): String {
  // A freshly-added custom exercise starts fully open (sets and reps both -1) rather than
  // "AMRAP x AMRAP reps" - that's not a real prescription, just "nothing set yet".
  if (exerciseSet.sets < 0 && exerciseSet.reps < 0 &&
    !exerciseSet.isToFailure && !exerciseSet.repsAreSequenced
  ) {
    // TODO localize
    return "Target not set yet"
  }
  val setsStr = when {
    exerciseSet.sets < 0 -> "AMRAP"
    else -> "${exerciseSet.sets} sets"
  }
  val repsStr = when {
    exerciseSet.isToFailure -> "to failure"
    exerciseSet.repsAreSequenced -> exerciseSet.repsSequence.joinToString("/")
    exerciseSet.reps < 0 -> "AMRAP reps"
    exerciseSet.repsRange > 0 -> "${exerciseSet.reps}-${exerciseSet.reps + exerciseSet.repsRange} reps"
    else -> "${exerciseSet.reps} reps"
  }
  val restStr = if (exerciseSet.rest > 0) " · ${exerciseSet.rest}s rest" else ""
  return "$setsStr × $repsStr$restStr"
}
