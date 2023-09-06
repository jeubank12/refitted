package com.litus_animae.refitted.compose.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse

// TODO this would go great with some unit tests
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun BoxWithConstraintsScope.NumberPicker(
  pageCount: Int,
  initialPage: Int,
  pageWidth: Dp,
  typography: TextStyle,
  pagerSnapDistance: Option<PagerSnapDistance> = None,
  onNumberSettled: (Int) -> Unit
) {
  val pagerState = rememberPagerState(initialPage)
  val flingBehavior = pagerSnapDistance.map{
    PagerDefaults.flingBehavior(
      state = pagerState,
      pagerSnapDistance = it
    )
  }.getOrElse { PagerDefaults.flingBehavior(state = pagerState) }

  LaunchedEffect(initialPage) {
    // FIXME if you change exercises too quickly you get an in-between value
    pagerState.animateScrollToPage(initialPage)
  }

  val currentOnNumberSettled by rememberUpdatedState(onNumberSettled)

  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }.collect {
      currentOnNumberSettled(it)
    }
  }

  HorizontalPager(
    pageCount = pageCount,
    Modifier.width(maxWidth),
    contentPadding = PaddingValues(horizontal = (maxWidth - pageWidth) / 2),
    state = pagerState,
    pageSize = PageSize.Fixed(pageWidth),
    flingBehavior = flingBehavior
  ) {
    val pageOffset = (
      (pagerState.currentPage - it) + pagerState
        .currentPageOffsetFraction
      )
    Box(
      Modifier
        .fillMaxSize()
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
          drawContent()
          if (pagerState.currentPage == it) {
            // noop
          } else if (pageOffset > 0) {
            // fade in from left
            // first to left has value 1.0 => 0.8
            val gradientStart = (pageOffset - 0.2f).coerceIn(0f, 1f)
            val gradientEnd = pageOffset.coerceIn(0f, 1f)
            drawRect(
              brush = Brush.horizontalGradient(
                gradientStart to Color.Transparent,
                gradientEnd to Color.Red
              ),
              blendMode = BlendMode.DstIn
            )
          } else if (pageOffset < 0) {
            // fade out to right
            // first to right has value -1.0 => 0/0.2
            val gradientStart = (pageOffset + 1f).coerceIn(0f, 1f)
            val gradientEnd = (pageOffset + 1.2f).coerceIn(0f, 1f)
            drawRect(
              brush = Brush.horizontalGradient(
                gradientStart to Color.Red,
                gradientEnd to Color.Transparent
              ),
              blendMode = BlendMode.DstIn
            )
          }
        },
      contentAlignment = BiasAlignment(pageOffset.coerceIn(-1f, 1f), 1f)
    ) {
      // FIXME 100 overflows
      Text(
        it.toString(),
        style = typography
      )
    }
  }
}