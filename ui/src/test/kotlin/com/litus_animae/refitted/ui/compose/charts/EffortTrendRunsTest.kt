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
}
