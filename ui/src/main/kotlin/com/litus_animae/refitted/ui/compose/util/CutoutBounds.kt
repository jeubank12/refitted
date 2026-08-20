package com.litus_animae.refitted.ui.compose.util

import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView

/**
 * The display cutout's actual bounding rects, in the window's pixel coordinate space (the same
 * space [androidx.compose.ui.layout.LayoutCoordinates.boundsInWindow] uses) - reactive to
 * rotation/fold/multi-window changes the same way `WindowInsets.displayCutout` is, but
 * preserving the cutout's real footprint instead of that API's one-rect-per-edge flattening.
 * There's no Compose-native equivalent of this reactivity for raw bounding rects, so this
 * reimplements it directly against the View tree via [View.setOnApplyWindowInsetsListener].
 * Cutouts are an API 28+ concept - always empty below that.
 */
@Composable
fun rememberDisplayCutoutBoundingRects(): List<Rect> {
  val view = LocalView.current
  var rects by remember(view) { mutableStateOf(view.currentCutoutRects()) }
  DisposableEffect(view) {
    val listener = View.OnApplyWindowInsetsListener { _, insets ->
      rects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        insets.displayCutout?.boundingRects?.map { it.toComposeRect() }.orEmpty()
      } else {
        emptyList()
      }
      insets
    }
    view.setOnApplyWindowInsetsListener(listener)
    // The listener only fires on the next dispatch - prime it with whatever's already attached.
    rects = view.currentCutoutRects()
    onDispose { view.setOnApplyWindowInsetsListener(null) }
  }
  return rects
}

private fun View.currentCutoutRects(): List<Rect> {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
  return rootWindowInsets?.displayCutout?.boundingRects?.map { it.toComposeRect() }.orEmpty()
}

private fun android.graphics.Rect.toComposeRect() =
  Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

/**
 * Whether a display cutout actually overlaps a pane's bounds, rather than just sharing the same
 * screen edge - `null` bounds (not yet measured, e.g. first composition) is conservative and
 * assumes affected, matching the pre-measurement behavior this replaces.
 */
fun cutoutAffects(paneBoundsInWindow: Rect?, cutoutRects: List<Rect>): Boolean =
  paneBoundsInWindow == null || cutoutRects.any { it.overlaps(paneBoundsInWindow) }
