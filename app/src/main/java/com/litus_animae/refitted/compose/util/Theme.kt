package com.litus_animae.refitted.compose.util

import androidx.compose.material.Colors
import androidx.compose.ui.graphics.Color

object Theme {

  val darkColors = Colors(
    primary = Color(0xFF0d47a1),
    primaryVariant = Color(0xFF002171),
    secondary = Color(0xFF212121),
    secondaryVariant = Color(0xFF000000),
    background = Color(0xFFffffff),
    surface = Color(0xFFffffff),
    error = Color(0xFFB00020),
    onPrimary = Color(0xFFffffff),
    onSecondary = Color(0xFFffffff),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    onError = Color(0xFFffffff),
    isLight = false
  )

  val lightColors = Colors(
    primary = Color(0xFF0d47a1),
    primaryVariant = Color(0xFF5472d3),
    secondary = Color(0xFF212121),
    secondaryVariant = Color(0xFF484848),
    background = Color(0xFFffffff),
    surface = Color(0xFFffffff),
    error = Color(0xFFB00020),
    onPrimary = Color(0xFFffffff),
    onSecondary = Color(0xFFffffff),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    onError = Color(0xFFffffff),
    isLight = true
  )
}