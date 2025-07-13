package com.litus_animae.refitted.ui.compose.exercise

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlin.random.Random

private const val minRotation = 1f
private const val maxRotation = 4f
private const val maxTranslation = 0.15f

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun PagerExerciseInstructions(
  instructions: List<ExerciseViewModel.ExerciseInstruction>,
  pagerState: PagerState,
  alternateIndex: Int?,
  contentPadding: PaddingValues
) {
// hint use set lists of colors and predictable rotations to debug
  val pageRotations = remember(instructions.size) {
    val rotation = maxRotation - minRotation
    0.rangeUntil(instructions.size)
      .map { _ -> Random.nextFloat() * (if (Random.nextBoolean()) -rotation else rotation) + minRotation }
      .toList()
  }
  val xTransforms = remember(instructions.size) {
    0.rangeUntil(instructions.size)
      .map { _ -> Random.nextFloat() * (if (Random.nextBoolean()) -maxTranslation else maxTranslation) + 0.5f }
      .toList()
  }
  val yTransforms = remember(instructions.size) {
    0.rangeUntil(instructions.size)
      .map { _ -> Random.nextFloat() * (if (Random.nextBoolean()) -maxTranslation else maxTranslation) + 0.5f }
      .toList()
  }
  Column {
    HorizontalPager(
      pagerState,
      // TODO fix the modifier
      Modifier.weight(5f, fill = true),
      beyondViewportPageCount = 1,
      contentPadding = contentPadding
    ) { page ->

      // Pages 1, 2, 3
      // X, 1(0), 2r+3r
      // X , 1(-0.5), 2+3r
      // 1, 2(0.5), 3r
      // 1r, 2, 3r
      // X, 2(-0.5), 3+1r
      // 2, 3(0.5), 1r
      // 2r, 3, 1r

      val offset by remember { derivedStateOf { pagerState.currentPageOffsetFraction } }
      val rotation = remember(page) { pageRotations[page % pageRotations.size] }
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
                  // not moving
                  -direction * widthInPx
                } else if (offset.sign.toInt() == direction) {
                  // swipe is toward this card
                  lerp(-direction * widthInPx, 0f, offset.absoluteValue * 2)
                } else {
                  -direction * widthInPx
                }
              }
            }
        ) {
          if ((direction > 0 && magnitude == 1) || page == instructions.size - 1) {
// need to consider offset and direction
            val endPage = if (offset < 0f) pagerState.currentPage - 1 else pagerState.currentPage
            (0.rangeUntil(endPage)).map { idx ->
              Card(
                Modifier
                  .zIndex(-(instructions.size) - idx.toFloat())
                  .fillMaxSize()
                  .graphicsLayer {
                    transformOrigin = TransformOrigin(
                      xTransforms[idx % xTransforms.size],
                      yTransforms[idx % yTransforms.size]
                    )
                    rotationZ = pageRotations[idx % pageRotations.size]
                  },
              ) {
                val instruction = instructions.getOrNull(idx)
                val exerciseSet by instruction?.set(alternateIndex)
                  ?.collectAsStateWithLifecycle(initialValue = null)
                  ?: remember { mutableStateOf<ExerciseSet?>(null) }
                ExerciseInstructions(exerciseSet)
              }
            }
            ((page + 1).rangeUntil(instructions.size)).map { idx ->
              Card(
                Modifier
                  .zIndex(idx * -1f)
                  .fillMaxSize()
                  .graphicsLayer {
                    transformOrigin = TransformOrigin(
                      xTransforms[idx % xTransforms.size],
                      yTransforms[idx % yTransforms.size]
                    )
                    rotationZ = pageRotations[idx % pageRotations.size]
                  },
              ) {
                val instruction = instructions.getOrNull(idx)
                val exerciseSet by instruction?.set(alternateIndex)
                  ?.collectAsStateWithLifecycle(initialValue = null)
                  ?: remember { mutableStateOf<ExerciseSet?>(null) }
                ExerciseInstructions(exerciseSet)
              }
            }
          }
          Card(
            Modifier
              .graphicsLayer {
                transformOrigin = TransformOrigin(
                  xTransforms[page % xTransforms.size],
                  yTransforms[page % yTransforms.size]
                )
                if (magnitude == 1) {
                  if (offset == 0f) {
                    // not moving
                    rotationZ = rotation
                  } else if (offset.sign.toInt() == direction) {
                    // swipe is toward this card
                    rotationZ = lerp(rotation, 0f, offset.absoluteValue * 2)
                  } else if (direction < 0) {
                    // swipe is away from this card
                    rotationZ = 0f
                    scaleX = 0.9f
                    scaleY = 0.9f
                    // TODO probably scale it down just a little to hide it
                  } else {
                    rotationZ = rotation
                  }
                } else if (magnitude != 0) {
                  rotationZ = rotation
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
              ExerciseInstructions(exerciseSet)
            }
          }
        }
      }
    }

//    ExerciseTimer(timeLimitMilliseconds = setWithRecord?.exerciseSet?.timeLimitMilliseconds)
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
          nonEmptyListOf(
            exerciseSet
          ),
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
  exerciseSet: ExerciseSet?
) {
  // TODO is there a way to show the scrollbar to indicate scrollability?
  // not yet, not implemented by the team -- possibly could use lazy state: https://developer.android.com/jetpack/compose/lists#react-to-scroll-position
  LazyColumn(
    // TODO maybe a fade out in the bottom?
    Modifier
      .fillMaxSize()
      .padding(top = 5.dp, start = 5.dp, end = 5.dp),
    contentPadding = PaddingValues(bottom = 5.dp)
  ) {
    stickyHeader {
      Surface(
        Modifier
          .fillMaxWidth()
          .padding(bottom = 5.dp)
      ) {
        val id = remember { Random.nextInt(5) }
        Text(text = "${id} ${exerciseSet?.exerciseName}" ?: "", style = MaterialTheme.typography.h4)
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
}
