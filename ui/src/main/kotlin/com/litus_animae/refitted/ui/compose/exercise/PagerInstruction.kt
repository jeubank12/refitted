package com.litus_animae.refitted.ui.compose.exercise

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arrow.core.nonEmptyListOf
import com.litus_animae.refitted.ui.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.ui.compose.util.Theme
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import com.litus_animae.refitted.data.models.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlin.random.Random

private val pageRotations = listOf(1f, -2f, 1.5f, -0.7f)
private val pageColors = listOf(Color.Red, Color.Cyan, Color.Green, Color.Blue)

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun PagerExerciseInstructions(
  instructions: List<ExerciseViewModel.ExerciseInstruction>,
  pagerState: PagerState,
  alternateIndex: Int?,
  contentPadding: PaddingValues
) {
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
      BoxWithConstraints(Modifier
        .zIndex(
          if (magnitude == 0) 0f
          else if (direction > 0) -2f
          else -3f
        )) {
        val widthInPx = with(LocalDensity.current){ maxWidth.toPx() }
        Box(
          Modifier
            .graphicsLayer {
              if (magnitude == 1) {
                translationX = if (offset == 0f) {
                  // not moving
                  // TODO translation needs to be a percent of the actual width
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
          if (direction > 0 && magnitude == 1) {
            if (pagerState.currentPage > 0)
              (0.rangeUntil(pagerState.currentPage)).map { idx ->
                Card(
                  Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = pageRotations[idx % pageRotations.size] },
                  backgroundColor = pageColors[idx]
                ) {}
              }
            (page.rangeUntil(instructions.size)).map { idx ->
              Card(
                Modifier
                  .fillMaxSize()
                  .graphicsLayer { rotationZ = pageRotations[idx % pageRotations.size] },
                backgroundColor = pageColors[idx]
              ) {}
            }
          }
          Card(
            Modifier
              .graphicsLayer {
                if (magnitude == 1) {
                  rotationZ = if (offset == 0f) {
                    // not moving
                    rotation
                  } else if (offset.sign.toInt() == direction) {
                    // swipe is toward this card
                    lerp(rotation, 0f, offset.absoluteValue * 2)
                  } else {
                    rotation
                  }
                } else if (magnitude != 0) {
                  rotationZ = rotation
                }
              },
            backgroundColor = pageColors[page]
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
    val pagerState = rememberPagerState { 3 }
    PagerExerciseInstructions(
      instructions = IntArray(3) { 1 }.asList().map { _ ->
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
