package com.litus_animae.refitted.ui.compose.exercise

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arrow.core.nonEmptyListOf
import com.litus_animae.refitted.ui.compose.util.Theme
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.sign

private const val minRotation = 1f
private const val maxRotation = 3f
private const val maxTranslation = 15f

/** Safe wrapped access — returns 0 if the list is empty (guards against divide-by-zero during initial composition). */
private fun List<Float>.wrapped(index: Int) = if (isEmpty()) 0f else get(index % size)

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun PagerExerciseInstructions(
  instructions: List<ExerciseViewModel.ExerciseInstruction>,
  pagerState: PagerState,
  alternateIndex: Int?,
  contentPadding: PaddingValues,
  /**
   * Records keyed by exercise-set ID. Each card looks up its own [numCompleted] so all
   * pre-composed pages have correct data without waiting for the parent to re-pass it.
   */
  setRecords: Map<String, com.litus_animae.refitted.ui.compose.state.ExerciseSetWithRecord> = emptyMap(),
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

  Column {
    HorizontalPager(
      pagerState,
      Modifier.weight(5f, fill = true),
      beyondViewportPageCount = 1,
      contentPadding = contentPadding
    ) { page ->

      val offset by remember { derivedStateOf { pagerState.currentPageOffsetFraction } }
      val rotation = remember(page) { pageRotations.wrapped(page) }
      val direction = (page - pagerState.currentPage).sign
      val magnitude = (page - pagerState.currentPage).absoluteValue
      BoxWithConstraints(
        Modifier
          .zIndex(
            if (magnitude == 0) 0f
            else if (direction > 0) -2f
            else -3f
          )
          .graphicsLayer {
            val directionFromCenter = (direction - offset).sign
            translationX = lerp(0f, directionFromCenter * 90f, offset.absoluteValue)
          }
      ) {
        val widthInPx = with(LocalDensity.current) { maxWidth.toPx() }
        Box(
          Modifier
            .graphicsLayer {
              if (magnitude == 1) {
                translationX = if (offset == 0f) {
                  -direction * widthInPx
                } else if (offset.sign.toInt() == direction) {
                  lerp(-direction * widthInPx, 0f, offset.absoluteValue * 2)
                } else {
                  -direction * widthInPx
                }
              }
            }
        ) {
          if ((direction > 0 && magnitude == 1) || page == instructions.size - 1) {
            val endPage = if (offset < 0f) pagerState.currentPage - 1 else pagerState.currentPage
            (0.rangeUntil(endPage)).map { idx ->
              Card(
                Modifier
                  .zIndex(-(instructions.size) - idx.toFloat())
                  .fillMaxSize()
                  .graphicsLayer {
                    translationX = xTransforms.wrapped(idx)
                    translationY = yTransforms.wrapped(idx)
                    rotationZ = pageRotations.wrapped(idx)
                  },
              ) {
                val instruction = instructions.getOrNull(idx)
                val exerciseSet by instruction?.set(alternateIndex)
                  ?.collectAsStateWithLifecycle(initialValue = null)
                  ?: remember { mutableStateOf<ExerciseSet?>(null) }
                ExerciseInstructions(exerciseSet, setRecords[exerciseSet?.id]?.numCompleted ?: 0)
              }
            }
            ((page + 1).rangeUntil(instructions.size)).map { idx ->
              Card(
                Modifier
                  .zIndex(idx * -1f)
                  .fillMaxSize()
                  .graphicsLayer {
                    translationX = xTransforms.wrapped(idx)
                    translationY = yTransforms.wrapped(idx)
                    rotationZ = pageRotations.wrapped(idx)
                  },
              ) {
                val instruction = instructions.getOrNull(idx)
                val exerciseSet by instruction?.set(alternateIndex)
                  ?.collectAsStateWithLifecycle(initialValue = null)
                  ?: remember { mutableStateOf<ExerciseSet?>(null) }
                ExerciseInstructions(exerciseSet, setRecords[exerciseSet?.id]?.numCompleted ?: 0)
              }
            }
          }
          Card(
            Modifier
              .graphicsLayer {
                if (magnitude == 1) {
                  if (offset == 0f) {
                    rotationZ = rotation
                    translationX = xTransforms.wrapped(page)
                    translationY = yTransforms.wrapped(page)
                  } else if (offset.sign.toInt() == direction) {
                    rotationZ = lerp(rotation, 0f, offset.absoluteValue * 2)
                    translationX =
                      lerp(xTransforms.wrapped(page), 0f, offset.absoluteValue * 2)
                    translationY =
                      lerp(yTransforms.wrapped(page), 0f, offset.absoluteValue * 2)
                  } else if (direction < 0) {
                    rotationZ = 0f
                    scaleX = 0.9f
                    scaleY = 0.9f
                  } else {
                    rotationZ = rotation
                  }
                } else if (magnitude != 0) {
                  rotationZ = rotation
                  translationX = xTransforms.wrapped(page)
                  translationY = yTransforms.wrapped(page)
                }
              },
          ) {
            CompositionLocalProvider(
              LocalOverscrollFactory provides null
            ) {
              val instruction = instructions.getOrNull(page)
              val exerciseSet by instruction?.set(alternateIndex)
                ?.collectAsStateWithLifecycle(initialValue = null)
                ?: remember { mutableStateOf<ExerciseSet?>(null) }
              // Each card self-serves its own progress from the records map — no reflow on swipe
              ExerciseInstructions(
                exerciseSet,
                numCompleted = setRecords[exerciseSet?.id]?.numCompleted ?: 0
              )
            }
          }
        }
      }
    }

    // ← page dots → navigation row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
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
    val exerciseSet by instruction?.set(alternateIndex)
      ?.collectAsStateWithLifecycle(initialValue = null)
      ?: remember { mutableStateOf<ExerciseSet?>(null) }

    ExerciseTimer(timeLimitMilliseconds = exerciseSet?.timeLimitMilliseconds)
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
      PaddingValues(16.dp)
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
