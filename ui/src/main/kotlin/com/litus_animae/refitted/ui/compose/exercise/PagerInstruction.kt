package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arrow.core.nonEmptyListOf
import com.litus_animae.refitted.ui.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.ui.compose.util.RefittedTheme
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sign

private const val minRotation = 1f
private const val maxRotation = 3f
private const val maxTranslation = 15f

private val deckCardElevation = 1.dp
private val focusedCardElevation = 6.dp

// The outgoing card accelerates away and is fully clear before the incoming one decelerates in,
// so the two never overlap - a simultaneous exit and enter along the same path just reads as one
// card wobbling out and back rather than as a swap.
private const val swapExitMillis = 200
private const val swapEnterDelayMillis = 160L
private const val swapEnterMillis = 220
// A card that flies in mid-query and then reflows once its description lands looks worse than a
// slightly longer beat before it arrives. Capped just past the exit so a slow or never-resolving
// query can only ever open a brief gap where neither card is on screen.
private const val swapContentTimeoutMillis = 240L

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

  val activeInstruction = instructions.getOrNull(pagerState.currentPage)
  // Keyed on the instruction's data (id and note), not just the id or the instruction object.
  // Using id alone would miss updates to note/sets/reps. Using the instruction object would
  // cause flickering on every unrelated edit (instructions rebuild wholesale). This includes
  // the note so edits appear immediately. The full set-data list (not just the head) is used
  // so adding or removing an alternate on *this* step still invalidates the key.
  val activeInstructionDataKey = activeInstruction?.sets?.map { "${it.id}|${it.note}" }
  val activeSetFlow = remember(activeInstructionDataKey, alternateIndex) {
    activeInstruction?.set(alternateIndex)
  }
  // Keyed by hand rather than via collectAsState, whose backing state is unkeyed: swiping to
  // another card would otherwise leave the previous card's set readable here until the new flow's
  // first emission, and the swap derivation below would read that as an alternate switch.
  val activeSetState = remember(activeSetFlow) { mutableStateOf<ExerciseSet?>(null) }
  LaunchedEffect(activeSetFlow) { activeSetFlow?.collect { activeSetState.value = it } }
  val activeSet by activeSetState

  // The set the card has settled on. It lags `activeSet` for exactly as long as a swap takes to
  // fly the difference between them, and is only ever advanced from an effect - deriving `swap`
  // purely means the overlay mounts and the pager slot hides in the same composition pass that
  // `activeSet` changes in, with no frame where the new content shows unanimated.
  // Reset per instruction so swiping to another card neither inherits its predecessor's set as a
  // swap origin nor leaves a stale swap running against a card that is no longer on top.
  var settledSet by remember(activeInstructionDataKey) { mutableStateOf<ExerciseSet?>(null) }
  // The first set to land in a card slot should just appear - only a real set replacing another
  // real set is an alternate switch.
  val swap = settledSet?.let { from ->
    activeSet?.takeIf { it.id != from.id }?.let { to -> AlternateSwap(from, to) }
  }
  LaunchedEffect(activeInstructionDataKey, activeSet?.id) {
    if (swap == null) settledSet = activeSet
  }

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
          // The swap overlay renders this card while it is in flight; this slot stays composed
          // (the pager still needs it for layout and gestures) but yields the pixels.
          hidden = swap != null && pagesFromCurrent == 0,
          // Share the same resolved set as the overlay for the settled, front-and-center page -
          // see the parameter doc on CardContent for why.
          resolvedSet = activeSet,
          hasResolvedSet = pagesFromCurrent == 0,
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

      // Last child of this Box, which - unlike the HorizontalPager above - does not clip, so the
      // card can fly clear of the screen without a separate window to escape into.
      swap?.let { activeSwap ->
        AlternateSwapOverlay(
          swap = activeSwap,
          instruction = activeInstruction,
          setRecords = setRecords,
          workoutPlan = workoutPlan,
          onAlternateChange = onAlternateChange,
          editing = editing,
          onDeleteExercise = onDeleteExercise,
          onEditNote = onEditNote,
          onSwapComplete = { settledSet = activeSwap.to },
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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

    ExerciseTimer(timeLimitMilliseconds = activeSet?.timeLimitMilliseconds)
  }
}

private data class AlternateSwap(val from: ExerciseSet, val to: ExerciseSet)

/**
 * Flies [AlternateSwap.from] off toward the upper right and pulls [AlternateSwap.to] in from the
 * same corner. Mounted only while a swap is in flight, so at rest the active card is composed
 * exactly once - by the pager slot this overlay temporarily stands in for.
 */
@OptIn(FlowPreview::class)
@Composable
private fun AlternateSwapOverlay(
  swap: AlternateSwap,
  instruction: ExerciseViewModel.ExerciseInstruction?,
  setRecords: Map<String, ExerciseSetWithRecord>,
  workoutPlan: WorkoutPlan?,
  onAlternateChange: (Int) -> Unit,
  editing: Boolean,
  onDeleteExercise: (ExerciseSet) -> Unit,
  onEditNote: (exerciseSet: ExerciseSet, note: String) -> Unit,
  onSwapComplete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Keyed on ids rather than on `swap` itself: an unrelated edit rebuilds the whole instructions
  // list into fresh ExerciseSet objects that never compare equal, which would restart a swap
  // already in flight.
  val exitProgress = remember(swap.from.id, swap.to.id) { Animatable(0f) }
  val enterProgress = remember(swap.from.id, swap.to.id) { Animatable(1f) }
  LaunchedEffect(swap.from.id, swap.to.id) {
    val exiting = launch {
      exitProgress.animateTo(1f, tween(swapExitMillis, easing = FastOutLinearInEasing))
    }
    val contentReady = launch {
      withTimeoutOrNull(swapContentTimeoutMillis) { swap.to.exercise.first { it != null } }
    }
    delay(swapEnterDelayMillis)
    contentReady.join()
    enterProgress.animateTo(0f, tween(swapEnterMillis, easing = LinearOutSlowInEasing))
    exiting.join()
    onSwapComplete()
  }

  @Composable
  fun SwapCard(exerciseSet: ExerciseSet, progress: State<Float>) {
    ExerciseCard(
      exerciseSet = exerciseSet,
      instruction = instruction,
      setRecords = setRecords,
      elevation = focusedCardElevation,
      workoutPlan = workoutPlan,
      onAlternateChange = onAlternateChange,
      editing = editing,
      onDeleteExercise = onDeleteExercise,
      onEditNote = onEditNote,
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          // Clear the card's own full bounds plus a margin, so it is completely offscreen at
          // the extremes rather than just peeking out from behind the corner.
          translationX = progress.value * (size.width + 32.dp.toPx())
          translationY = -progress.value * (size.height + 32.dp.toPx())
          rotationZ = progress.value * 18f
        }
    )
  }

  Box(modifier) {
    // Incoming last so it lands on top of any residual overlap with the outgoing card.
    SwapCard(swap.from, exitProgress.asState())
    SwapCard(swap.to, enterProgress.asState())
  }
}

@OptIn(FlowPreview::class)
@Composable
private fun CardContent(
  instruction: ExerciseViewModel.ExerciseInstruction?,
  alternateIndex: Int?,
  setRecords: Map<String, ExerciseSetWithRecord>,
  /** Continuous 0..1 distance-from-center, in pages — 0 for deck cards, which never focus. */
  pageFocus: Float = 0f,
  /** Yields the pixels to the swap overlay without giving up the slot's layout or gesture area. */
  hidden: Boolean = false,
  /**
   * The active page passes the same set the overlay animates in, rather than resolving its own
   * copy - two independently-timed subscriptions to the same data can settle a frame apart,
   * which shows as the card's content reverting right as [hidden] clears. `hasResolvedSet` (not
   * just null-checking [resolvedSet]) distinguishes "the parent resolved this to null" from
   * "no override was passed" for the deck's and other pages' own copies, which still resolve
   * their own set below.
   */
  resolvedSet: ExerciseSet? = null,
  hasResolvedSet: Boolean = false,
  workoutPlan: WorkoutPlan? = null,
  onAlternateChange: (Int) -> Unit = {},
  modifier: Modifier = Modifier,
  editing: Boolean = false,
  onDeleteExercise: (ExerciseSet) -> Unit = {},
  onEditNote: (exerciseSet: ExerciseSet, note: String) -> Unit = { _, _ -> },
) {
  // Keyed on the instruction's stable identity (every set id it currently covers), not the
  // instruction object itself - every edit anywhere rebuilds the whole instructions list into
  // fresh ExerciseInstruction/ExerciseSet objects (each carrying a newly-queried
  // `exercise: Flow<Exercise?>` field that's never equal across reloads), so keying on
  // `instruction` would recreate this flow - and reset the collected state to null for a frame -
  // on every unrelated edit, not just this card's own. The full set-id list (not just the head
  // id) is used so adding or removing an alternate on *this* step still invalidates the key.
  val exerciseSetFlow = remember(instruction?.sets?.map { it.id }, alternateIndex) {
    instruction?.set(alternateIndex)
  }
  val ownExerciseSet by exerciseSetFlow
    ?.collectAsStateWithLifecycle(initialValue = null)
    ?: remember { mutableStateOf(null) }
  val exerciseSet = if (hasResolvedSet) resolvedSet else ownExerciseSet

  ExerciseCard(
    exerciseSet = exerciseSet,
    instruction = instruction,
    setRecords = setRecords,
    // Driven straight off pageFocus (the page's continuous distance from center) rather than a
    // boolean + tween — tracks the drag itself instead of jumping into an animation at the
    // halfway point where the settled page flips.
    elevation = lerp(deckCardElevation, focusedCardElevation, pageFocus),
    workoutPlan = workoutPlan,
    onAlternateChange = onAlternateChange,
    editing = editing,
    onDeleteExercise = onDeleteExercise,
    onEditNote = onEditNote,
    modifier = modifier.alpha(if (hidden) 0f else 1f)
  )
}

/**
 * Known issue: this card's rounded corners render square for the duration of the NavHost fade
 * transition when first navigating in from the calendar, self-correcting once the fade settles —
 * a Compose framework bug, not caused by [elevation]. See
 * https://issuetracker.google.com/issues/375496210.
 */
@OptIn(FlowPreview::class)
@Composable
private fun ExerciseCard(
  exerciseSet: ExerciseSet?,
  instruction: ExerciseViewModel.ExerciseInstruction?,
  setRecords: Map<String, ExerciseSetWithRecord>,
  /** Passed to Card's own param, not just a graphicsLayer shadow, so the tonal surface color moves with it too. */
  elevation: Dp,
  workoutPlan: WorkoutPlan?,
  onAlternateChange: (Int) -> Unit,
  editing: Boolean,
  onDeleteExercise: (ExerciseSet) -> Unit,
  onEditNote: (exerciseSet: ExerciseSet, note: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(modifier, elevation = CardDefaults.cardElevation(defaultElevation = elevation)) {
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
      // Each card self-serves its own progress from the records map — no reflow on swipe
      ExerciseInstructions(
        exerciseSet,
        setRecords[exerciseSet?.id]?.numCompleted ?: 0,
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

@OptIn(FlowPreview::class)
@Preview(showBackground = true, widthDp = 400, heightDp = 400)
@Preview(showBackground = true, widthDp = 400, heightDp = 500)
@Preview(showBackground = true, widthDp = 300, heightDp = 300)
@Composable
private fun PreviewPagerExerciseInstructions(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
  RefittedTheme(darkTheme = true) {
    val pagerState = rememberPagerState { 4 }
    PagerExerciseInstructions(
      instructions = IntArray(4) { 1 }.asList().map { _ ->
        ExerciseViewModel.ExerciseInstruction(
          nonEmptyListOf(exerciseSet),
          null,
          MutableStateFlow(0),
          ExerciseViewModel.AlternateSelection()
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
  RefittedTheme(darkTheme = true) {
    val pagerState = rememberPagerState { 1 }
    val alternateSet = exampleExerciseSet.copy(step = "1.a", name = "B_Dumbbell Press")
    PagerExerciseInstructions(
      instructions = listOf(
        ExerciseViewModel.ExerciseInstruction(
          nonEmptyListOf(exampleExerciseSet, alternateSet),
          null,
          MutableStateFlow(0),
          ExerciseViewModel.AlternateSelection()
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
  // Must match the actual Card's own container color (its M3 tonal default, not plain
  // colorScheme.surface) or the gradient fades to a visibly different color than the card
  // background it's sitting on - was fading to something close to black in dark mode before this
  // tracked the real default.
  val cardColor = CardDefaults.cardColors().containerColor
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
        Surface(Modifier.fillMaxWidth().padding(bottom = 4.dp), color = cardColor) {
          Column {
            Text(
              text = exerciseSet?.exerciseName ?: "",
              // M3's headlineMedium defaults to Regular weight; M2's h4 (what this replaced)
              // read bolder at this size - bold explicitly to keep the exercise name prominent.
              style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            if (exerciseSet != null) {
              val prescriptionText = buildPrescriptionText(exerciseSet)
              Text(
                text = prescriptionText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
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
