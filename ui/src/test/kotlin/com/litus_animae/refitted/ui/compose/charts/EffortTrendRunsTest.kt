package com.litus_animae.refitted.ui.compose.charts

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class EffortTrendRunsTest {

  @Test
  internal fun `a null value breaks the run`() {
    val runs = buildTrendRuns(listOf(0f to 10.0, 1f to null, 2f to 20.0))
    assertThat(runs).hasSize(2)
    assertThat(runs[0]).containsExactly(0f to 10f)
    assertThat(runs[1]).containsExactly(2f to 20f)
  }

  @Test
  internal fun `every point connects directly to its neighbor, no step`() {
    val runs = buildTrendRuns(listOf(0f to 10.0, 1f to 10.0, 2f to 20.0))
    assertThat(runs.single()).containsExactly(0f to 10f, 1f to 10f, 2f to 20f).inOrder()
  }

  @Test
  internal fun `collapseSessions reduces a collapsed session to its own midpoint`() {
    val points = listOf(
      BandPoint(0f, 95.0, sessionIndex = 0, collapsed = true),
      BandPoint(1f, 95.0, sessionIndex = 0, collapsed = true),
      BandPoint(2f, 95.0, sessionIndex = 0, collapsed = true)
    )
    assertThat(collapseSessions(points)).containsExactly(1f to 95.0)
  }

  @Test
  internal fun `collapseSessions leaves uncollapsed points untouched, one per input point`() {
    val points = listOf(
      BandPoint(0f, 95.0, sessionIndex = 0, collapsed = false),
      BandPoint(1f, 100.0, sessionIndex = 0, collapsed = false),
      BandPoint(2f, 105.0, sessionIndex = 1, collapsed = false)
    )
    assertThat(collapseSessions(points))
      .containsExactly(0f to 95.0, 1f to 100.0, 2f to 105.0)
      .inOrder()
  }

  @Test
  internal fun `collapseSessions passes a null value through unchanged`() {
    val points = listOf(
      BandPoint(0f, 95.0, sessionIndex = 0, collapsed = true),
      BandPoint(1f, null, sessionIndex = 1, collapsed = false),
      BandPoint(2f, 105.0, sessionIndex = 2, collapsed = true)
    )
    assertThat(collapseSessions(points))
      .containsExactly(0f to 95.0, 1f to null, 2f to 105.0)
      .inOrder()
  }

  @Test
  internal fun `a session boundary produces one point per session, connected directly`() {
    // Two 3-set collapsed sessions back to back, followed by two live/uncollapsed sets.
    val points = listOf(
      BandPoint(0f, 95.0, sessionIndex = 0, collapsed = true),
      BandPoint(1f, 95.0, sessionIndex = 0, collapsed = true),
      BandPoint(2f, 95.0, sessionIndex = 0, collapsed = true),
      BandPoint(3f, 110.0, sessionIndex = 1, collapsed = true),
      BandPoint(4f, 110.0, sessionIndex = 1, collapsed = true),
      BandPoint(5f, 110.0, sessionIndex = 1, collapsed = true),
      BandPoint(6f, 112.0, sessionIndex = 2, collapsed = false),
      BandPoint(7f, 118.0, sessionIndex = 2, collapsed = false)
    )
    val reduced = collapseSessions(points)
    assertThat(reduced).containsExactly(1f to 95.0, 4f to 110.0, 6f to 112.0, 7f to 118.0).inOrder()

    val runs = buildTrendRuns(reduced)
    assertThat(runs.single())
      .containsExactly(1f to 95f, 4f to 110f, 6f to 112f, 7f to 118f)
      .inOrder()
  }
}
