package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adaptive two-pane layout that reflows between portrait (vertical split) and landscape
 * (horizontal split) based on constraints alone — NOT a composition branch.
 *
 * Because both children are always emitted in the same call position and only their
 * measurement/placement changes, [rememberSaveable] state inside each pane survives
 * rotation without being re-keyed.
 *
 * @param first  Top pane in portrait; left pane in landscape.
 * @param second Bottom pane in portrait; right pane in landscape.
 */
@Composable
fun AdaptiveExercisePanes(
  modifier: Modifier = Modifier,
  splitRatio: Float = 0.5f,
  gap: Dp = 16.dp,
  first: @Composable () -> Unit,
  second: @Composable () -> Unit,
) {
  Layout(
    modifier = modifier,
    content = { first(); second() }
  ) { measurables, constraints ->
    require(measurables.size == 2) { "AdaptiveExercisePanes expects exactly 2 children" }
    val gapPx = gap.roundToPx()
    val landscape = constraints.maxWidth > constraints.maxHeight

    // Force each child to exactly fill its half so that Modifier.weight() inside them works.
    val firstPlaceable = if (landscape) {
      val w = ((constraints.maxWidth - gapPx) * splitRatio).toInt()
      measurables[0].measure(constraints.copy(minWidth = w, maxWidth = w))
    } else {
      val h = ((constraints.maxHeight - gapPx) * splitRatio).toInt()
      measurables[0].measure(constraints.copy(minHeight = h, maxHeight = h))
    }

    val secondPlaceable = if (landscape) {
      val w = (constraints.maxWidth - gapPx - firstPlaceable.width).coerceAtLeast(0)
      measurables[1].measure(constraints.copy(minWidth = w, maxWidth = w))
    } else {
      val h = (constraints.maxHeight - gapPx - firstPlaceable.height).coerceAtLeast(0)
      measurables[1].measure(constraints.copy(minHeight = h, maxHeight = h))
    }

    layout(constraints.maxWidth, constraints.maxHeight) {
      if (landscape) {
        firstPlaceable.placeRelative(0, 0)
        secondPlaceable.placeRelative(firstPlaceable.width + gapPx, 0)
      } else {
        firstPlaceable.placeRelative(0, 0)
        secondPlaceable.placeRelative(0, firstPlaceable.height + gapPx)
      }
    }
  }
}
