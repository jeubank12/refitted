package com.litus_animae.refitted.ui.compose.util

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CutoutBoundsTest {
  private val paneBounds = Rect(0f, 0f, 100f, 100f)

  @Test
  internal fun `unmeasured pane bounds are treated as affected`() {
    assertTrue(cutoutAffects(null, listOf(Rect(0f, 0f, 10f, 10f))))
  }

  @Test
  internal fun `no cutouts means unaffected`() {
    assertFalse(cutoutAffects(paneBounds, emptyList()))
  }

  @Test
  internal fun `cutout entirely outside pane bounds is unaffected`() {
    val cutout = Rect(200f, 200f, 210f, 210f)
    assertFalse(cutoutAffects(paneBounds, listOf(cutout)))
  }

  @Test
  internal fun `cutout partially overlapping pane bounds is affected`() {
    val cutout = Rect(90f, 90f, 110f, 110f)
    assertTrue(cutoutAffects(paneBounds, listOf(cutout)))
  }

  @Test
  internal fun `cutout fully inside pane bounds is affected`() {
    val cutout = Rect(40f, 40f, 60f, 60f)
    assertTrue(cutoutAffects(paneBounds, listOf(cutout)))
  }

  @Test
  internal fun `one of multiple cutouts overlapping is enough to affect`() {
    val farCutout = Rect(500f, 500f, 510f, 510f)
    val overlappingCutout = Rect(0f, 0f, 5f, 5f)
    assertTrue(cutoutAffects(paneBounds, listOf(farCutout, overlappingCutout)))
  }
}
