package com.litus_animae.refitted.ui.compose.exercise

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arrow.core.nonEmptyListOf
import com.litus_animae.refitted.ui.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.ui.compose.util.Theme
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
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
  /**
   * Records keyed by exercise-set ID. Each card looks up its own [numCompleted] so all
   * pre-composed pages have correct data without waiting for the parent to re-pass it.
   */
  setRecords: Map<String, ExerciseSetWithRecord> = emptyMap(),
) {
  val pageRotations = remember(instructions.size) {
    val rotation = maxRotation - minRotation
    0.rangeUntil(instructions.size)
      .map { _ ->
        val r = (minRotation + Math.random().toFloat() * rotation)
        if (Math.random() < 0.5) -r else r
      }
      .toList()
  }
  val xTransforms = remember(instructions.size) {
    0.rangeUntil(instructions.size)
      .map { _ ->
        val t = Math.random().toFloat() * maxTranslation
        if (Math.random() < 0.5) -t else t
      }
      .toList()
  }
  val yTransforms = remember(instructions.size) {
    0.rangeUntil(instructions.size)
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
            Card(
              Modifier
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
                    // card, returning to centre as the swipe settles.
                    translationX = xTransforms.wrapped(idx) +
                      spread * min(settleFraction, 1f - settleFraction)
                    translationY = yTransforms.wrapped(idx)
                    rotationZ = pageRotations.wrapped(idx)
                  }
                }
            ) {
              CardContent(instruction, alternateIndex, setRecords)
            }
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
        Card(
          Modifier
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
              // Continuous distance of this page from front-and-centre, in pages.
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
        ) {
          CardContent(instructions.getOrNull(page), alternateIndex, setRecords)
        }
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
    val exerciseSetFlow = remember(instruction, alternateIndex) { instruction?.set(alternateIndex) }
    val exerciseSet by exerciseSetFlow
      ?.collectAsStateWithLifecycle(initialValue = null)
      ?: remember { mutableStateOf<ExerciseSet?>(null) }

    ExerciseTimer(timeLimitMilliseconds = exerciseSet?.timeLimitMilliseconds)
  }
}

@OptIn(FlowPreview::class)
@Composable
private fun CardContent(
  instruction: ExerciseViewModel.ExerciseInstruction?,
  alternateIndex: Int?,
  setRecords: Map<String, ExerciseSetWithRecord>,
) {
  CompositionLocalProvider(
    LocalOverscrollFactory provides null
  ) {
    val exerciseSetFlow = remember(instruction, alternateIndex) { instruction?.set(alternateIndex) }
    val exerciseSet by exerciseSetFlow
      ?.collectAsStateWithLifecycle(initialValue = null)
      ?: remember { mutableStateOf<ExerciseSet?>(null) }
    // Each card self-serves its own progress from the records map — no reflow on swipe
    ExerciseInstructions(exerciseSet, setRecords[exerciseSet?.id]?.numCompleted ?: 0)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseInstructions(
  exerciseSet: ExerciseSet?,
  /** Defaults to 0 so the progress line always occupies space from first paint — no layout jump. */
  numCompleted: Int = 0,
) {
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
          val exercise by exerciseSet.exercise.collectAsStateWithLifecycle(null)
          Text(exercise?.description ?: "", Modifier.padding(bottom = 5.dp))
        }
      }
      item {
        Text(exerciseSet?.note ?: "")
      }
    }

    // Set progress pinned to the bottom of the card — always occupies space, no layout jump
    if (exerciseSet != null && exerciseSet.sets > 0) {
      Text(
        text = "Set ${numCompleted + 1} of ${exerciseSet.sets}",
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.primary,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 12.dp)
      )
    }
  }
}

/** Formats a human-readable prescription string from an [ExerciseSet]. */
private fun buildPrescriptionText(exerciseSet: ExerciseSet): String {
  val setsStr = when {
    exerciseSet.sets < 0 -> "AMRAP"
    else -> "${exerciseSet.sets} sets"
  }
  val repsStr = when {
    exerciseSet.isToFailure -> "to failure"
    exerciseSet.repsAreSequenced -> exerciseSet.repsSequence.joinToString("/")
    exerciseSet.reps < 0 -> "AMRAP reps"
    else -> "${exerciseSet.reps} reps"
  }
  val restStr = if (exerciseSet.rest > 0) " · ${exerciseSet.rest}s rest" else ""
  return "$setsStr × $repsStr$restStr"
}
