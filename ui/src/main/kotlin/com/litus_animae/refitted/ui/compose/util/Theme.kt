package com.litus_animae.refitted.ui.compose.util

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object Theme {

  val timerAmber = Color(0xFFFFB300)   // warning/almost-done arc colour
  val goodAttention = Color(0xFF2E7D32)   // draws the eye for a positive reason — timer finish blink, today marker
  val timerTrack = Color(0x1F000000)   // muted track behind the arc — both palettes use a white surface/background

  // primaryVariant/secondaryVariant (M2's Colors) have no M3 slot, so they're dropped here; every
  // other ColorScheme slot not listed below is filled in by the light/darkColorScheme builder
  // defaults, since no call site in this module reaches them today.
  val darkScheme = darkColorScheme(
    primary = Color(0xFF0d47a1),
    secondary = Color(0xFF212121),
    background = Color(0xFFffffff),
    surface = Color(0xFFffffff),
    error = Color(0xFFB00020),
    onPrimary = Color(0xFFffffff),
    onSecondary = Color(0xFFffffff),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    onError = Color(0xFFffffff),
  )

  val lightScheme = lightColorScheme(
    primary = Color(0xFF0d47a1),
    secondary = Color(0xFF212121),
    background = Color(0xFFffffff),
    surface = Color(0xFFffffff),
    error = Color(0xFFB00020),
    onPrimary = Color(0xFFffffff),
    onSecondary = Color(0xFFffffff),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    onError = Color(0xFFffffff),
  )
}