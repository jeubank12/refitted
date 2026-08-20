package com.litus_animae.refitted.ui.compose.util

import android.app.UiModeManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

// Generated palette lives in ThemeColors.kt (Material Theme Builder export - see its header
// comment for how to regenerate). Brand colors only, no dynamic/wallpaper-based color: see
// ui/CLAUDE.md for why.

private val lightScheme = lightColorScheme(
  // Theme Builder recalculated "primary" onto its own tonal scale rather than preserving the
  // brand blue verbatim, landing it a shade duller than before - the exact original brand blue
  // (#0D47A1) ended up in primaryContainer instead, a role almost nothing here reads. Swapped so
  // primary/onPrimary get the brand blue back; onPrimary/onPrimaryContainer keep their generated
  // values since both still contrast fine against either value (checked: 8.7:1 and 6.6:1).
  primary = primaryContainerLight,
  onPrimary = onPrimaryLight,
  primaryContainer = primaryLight,
  onPrimaryContainer = onPrimaryContainerLight,
  secondary = secondaryLight,
  onSecondary = onSecondaryLight,
  secondaryContainer = secondaryContainerLight,
  onSecondaryContainer = onSecondaryContainerLight,
  tertiary = tertiaryLight,
  onTertiary = onTertiaryLight,
  tertiaryContainer = tertiaryContainerLight,
  onTertiaryContainer = onTertiaryContainerLight,
  error = errorLight,
  onError = onErrorLight,
  errorContainer = errorContainerLight,
  onErrorContainer = onErrorContainerLight,
  background = backgroundLight,
  onBackground = onBackgroundLight,
  surface = surfaceLight,
  onSurface = onSurfaceLight,
  surfaceVariant = surfaceVariantLight,
  onSurfaceVariant = onSurfaceVariantLight,
  outline = outlineLight,
  outlineVariant = outlineVariantLight,
  scrim = scrimLight,
  inverseSurface = inverseSurfaceLight,
  inverseOnSurface = inverseOnSurfaceLight,
  inversePrimary = inversePrimaryLight,
  surfaceDim = surfaceDimLight,
  surfaceBright = surfaceBrightLight,
  surfaceContainerLowest = surfaceContainerLowestLight,
  surfaceContainerLow = surfaceContainerLowLight,
  surfaceContainer = surfaceContainerLight,
  surfaceContainerHigh = surfaceContainerHighLight,
  surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
  primary = primaryDark,
  onPrimary = onPrimaryDark,
  primaryContainer = primaryContainerDark,
  onPrimaryContainer = onPrimaryContainerDark,
  secondary = secondaryDark,
  onSecondary = onSecondaryDark,
  secondaryContainer = secondaryContainerDark,
  onSecondaryContainer = onSecondaryContainerDark,
  tertiary = tertiaryDark,
  onTertiary = onTertiaryDark,
  tertiaryContainer = tertiaryContainerDark,
  onTertiaryContainer = onTertiaryContainerDark,
  error = errorDark,
  onError = onErrorDark,
  errorContainer = errorContainerDark,
  onErrorContainer = onErrorContainerDark,
  background = backgroundDark,
  onBackground = onBackgroundDark,
  surface = surfaceDark,
  onSurface = onSurfaceDark,
  surfaceVariant = surfaceVariantDark,
  onSurfaceVariant = onSurfaceVariantDark,
  outline = outlineDark,
  outlineVariant = outlineVariantDark,
  scrim = scrimDark,
  inverseSurface = inverseSurfaceDark,
  inverseOnSurface = inverseOnSurfaceDark,
  inversePrimary = inversePrimaryDark,
  surfaceDim = surfaceDimDark,
  surfaceBright = surfaceBrightDark,
  surfaceContainerLowest = surfaceContainerLowestDark,
  surfaceContainerLow = surfaceContainerLowDark,
  surfaceContainer = surfaceContainerDark,
  surfaceContainerHigh = surfaceContainerHighDark,
  surfaceContainerHighest = surfaceContainerHighestDark,
)

private val lightMediumContrastScheme = lightColorScheme(
  // Same primary/primaryContainer swap as lightScheme above - this tier mirrored the same two
  // values, so it needs the same fix for consistency between contrast levels.
  primary = primaryContainerLightMediumContrast,
  onPrimary = onPrimaryLightMediumContrast,
  primaryContainer = primaryLightMediumContrast,
  onPrimaryContainer = onPrimaryContainerLightMediumContrast,
  secondary = secondaryLightMediumContrast,
  onSecondary = onSecondaryLightMediumContrast,
  secondaryContainer = secondaryContainerLightMediumContrast,
  onSecondaryContainer = onSecondaryContainerLightMediumContrast,
  tertiary = tertiaryLightMediumContrast,
  onTertiary = onTertiaryLightMediumContrast,
  tertiaryContainer = tertiaryContainerLightMediumContrast,
  onTertiaryContainer = onTertiaryContainerLightMediumContrast,
  error = errorLightMediumContrast,
  onError = onErrorLightMediumContrast,
  errorContainer = errorContainerLightMediumContrast,
  onErrorContainer = onErrorContainerLightMediumContrast,
  background = backgroundLightMediumContrast,
  onBackground = onBackgroundLightMediumContrast,
  surface = surfaceLightMediumContrast,
  onSurface = onSurfaceLightMediumContrast,
  surfaceVariant = surfaceVariantLightMediumContrast,
  onSurfaceVariant = onSurfaceVariantLightMediumContrast,
  outline = outlineLightMediumContrast,
  outlineVariant = outlineVariantLightMediumContrast,
  scrim = scrimLightMediumContrast,
  inverseSurface = inverseSurfaceLightMediumContrast,
  inverseOnSurface = inverseOnSurfaceLightMediumContrast,
  inversePrimary = inversePrimaryLightMediumContrast,
  surfaceDim = surfaceDimLightMediumContrast,
  surfaceBright = surfaceBrightLightMediumContrast,
  surfaceContainerLowest = surfaceContainerLowestLightMediumContrast,
  surfaceContainerLow = surfaceContainerLowLightMediumContrast,
  surfaceContainer = surfaceContainerLightMediumContrast,
  surfaceContainerHigh = surfaceContainerHighLightMediumContrast,
  surfaceContainerHighest = surfaceContainerHighestLightMediumContrast,
)

private val lightHighContrastScheme = lightColorScheme(
  primary = primaryLightHighContrast,
  onPrimary = onPrimaryLightHighContrast,
  primaryContainer = primaryContainerLightHighContrast,
  onPrimaryContainer = onPrimaryContainerLightHighContrast,
  secondary = secondaryLightHighContrast,
  onSecondary = onSecondaryLightHighContrast,
  secondaryContainer = secondaryContainerLightHighContrast,
  onSecondaryContainer = onSecondaryContainerLightHighContrast,
  tertiary = tertiaryLightHighContrast,
  onTertiary = onTertiaryLightHighContrast,
  tertiaryContainer = tertiaryContainerLightHighContrast,
  onTertiaryContainer = onTertiaryContainerLightHighContrast,
  error = errorLightHighContrast,
  onError = onErrorLightHighContrast,
  errorContainer = errorContainerLightHighContrast,
  onErrorContainer = onErrorContainerLightHighContrast,
  background = backgroundLightHighContrast,
  onBackground = onBackgroundLightHighContrast,
  surface = surfaceLightHighContrast,
  onSurface = onSurfaceLightHighContrast,
  surfaceVariant = surfaceVariantLightHighContrast,
  onSurfaceVariant = onSurfaceVariantLightHighContrast,
  outline = outlineLightHighContrast,
  outlineVariant = outlineVariantLightHighContrast,
  scrim = scrimLightHighContrast,
  inverseSurface = inverseSurfaceLightHighContrast,
  inverseOnSurface = inverseOnSurfaceLightHighContrast,
  inversePrimary = inversePrimaryLightHighContrast,
  surfaceDim = surfaceDimLightHighContrast,
  surfaceBright = surfaceBrightLightHighContrast,
  surfaceContainerLowest = surfaceContainerLowestLightHighContrast,
  surfaceContainerLow = surfaceContainerLowLightHighContrast,
  surfaceContainer = surfaceContainerLightHighContrast,
  surfaceContainerHigh = surfaceContainerHighLightHighContrast,
  surfaceContainerHighest = surfaceContainerHighestLightHighContrast,
)

private val darkMediumContrastScheme = darkColorScheme(
  primary = primaryDarkMediumContrast,
  onPrimary = onPrimaryDarkMediumContrast,
  primaryContainer = primaryContainerDarkMediumContrast,
  onPrimaryContainer = onPrimaryContainerDarkMediumContrast,
  secondary = secondaryDarkMediumContrast,
  onSecondary = onSecondaryDarkMediumContrast,
  secondaryContainer = secondaryContainerDarkMediumContrast,
  onSecondaryContainer = onSecondaryContainerDarkMediumContrast,
  tertiary = tertiaryDarkMediumContrast,
  onTertiary = onTertiaryDarkMediumContrast,
  tertiaryContainer = tertiaryContainerDarkMediumContrast,
  onTertiaryContainer = onTertiaryContainerDarkMediumContrast,
  error = errorDarkMediumContrast,
  onError = onErrorDarkMediumContrast,
  errorContainer = errorContainerDarkMediumContrast,
  onErrorContainer = onErrorContainerDarkMediumContrast,
  background = backgroundDarkMediumContrast,
  onBackground = onBackgroundDarkMediumContrast,
  surface = surfaceDarkMediumContrast,
  onSurface = onSurfaceDarkMediumContrast,
  surfaceVariant = surfaceVariantDarkMediumContrast,
  onSurfaceVariant = onSurfaceVariantDarkMediumContrast,
  outline = outlineDarkMediumContrast,
  outlineVariant = outlineVariantDarkMediumContrast,
  scrim = scrimDarkMediumContrast,
  inverseSurface = inverseSurfaceDarkMediumContrast,
  inverseOnSurface = inverseOnSurfaceDarkMediumContrast,
  inversePrimary = inversePrimaryDarkMediumContrast,
  surfaceDim = surfaceDimDarkMediumContrast,
  surfaceBright = surfaceBrightDarkMediumContrast,
  surfaceContainerLowest = surfaceContainerLowestDarkMediumContrast,
  surfaceContainerLow = surfaceContainerLowDarkMediumContrast,
  surfaceContainer = surfaceContainerDarkMediumContrast,
  surfaceContainerHigh = surfaceContainerHighDarkMediumContrast,
  surfaceContainerHighest = surfaceContainerHighestDarkMediumContrast,
)

private val darkHighContrastScheme = darkColorScheme(
  primary = primaryDarkHighContrast,
  onPrimary = onPrimaryDarkHighContrast,
  primaryContainer = primaryContainerDarkHighContrast,
  onPrimaryContainer = onPrimaryContainerDarkHighContrast,
  secondary = secondaryDarkHighContrast,
  onSecondary = onSecondaryDarkHighContrast,
  secondaryContainer = secondaryContainerDarkHighContrast,
  onSecondaryContainer = onSecondaryContainerDarkHighContrast,
  tertiary = tertiaryDarkHighContrast,
  onTertiary = onTertiaryDarkHighContrast,
  tertiaryContainer = tertiaryContainerDarkHighContrast,
  onTertiaryContainer = onTertiaryContainerDarkHighContrast,
  error = errorDarkHighContrast,
  onError = onErrorDarkHighContrast,
  errorContainer = errorContainerDarkHighContrast,
  onErrorContainer = onErrorContainerDarkHighContrast,
  background = backgroundDarkHighContrast,
  onBackground = onBackgroundDarkHighContrast,
  surface = surfaceDarkHighContrast,
  onSurface = onSurfaceDarkHighContrast,
  surfaceVariant = surfaceVariantDarkHighContrast,
  onSurfaceVariant = onSurfaceVariantDarkHighContrast,
  outline = outlineDarkHighContrast,
  outlineVariant = outlineVariantDarkHighContrast,
  scrim = scrimDarkHighContrast,
  inverseSurface = inverseSurfaceDarkHighContrast,
  inverseOnSurface = inverseOnSurfaceDarkHighContrast,
  inversePrimary = inversePrimaryDarkHighContrast,
  surfaceDim = surfaceDimDarkHighContrast,
  surfaceBright = surfaceBrightDarkHighContrast,
  surfaceContainerLowest = surfaceContainerLowestDarkHighContrast,
  surfaceContainerLow = surfaceContainerLowDarkHighContrast,
  surfaceContainer = surfaceContainerDarkHighContrast,
  surfaceContainerHigh = surfaceContainerHighDarkHighContrast,
  surfaceContainerHighest = surfaceContainerHighestDarkHighContrast,
)

val LocalExtendedColors = staticCompositionLocalOf { extendedLight }

/** Access point for this app's extended (non-M3-standard) semantic colors - `ExtendedTheme.colors.goodAttention.color`. */
object ExtendedTheme {
  val colors: ExtendedColorScheme
    @Composable get() = LocalExtendedColors.current
}

private enum class ContrastLevel { STANDARD, MEDIUM, HIGH }

// UiModeManager.getContrast() returns 0f..1f with no published named thresholds; Android's
// system contrast setting itself only has 3 stops (standard/medium/high, ~= 0f/0.5f/1f), so this
// splits the difference between them. Verify against a real Android 14+ device's Settings >
// Accessibility > Contrast slider if the bucketing looks wrong in practice.
private fun contrastLevelFor(contrast: Float): ContrastLevel = when {
  contrast >= 0.75f -> ContrastLevel.HIGH
  contrast >= 0.25f -> ContrastLevel.MEDIUM
  else -> ContrastLevel.STANDARD
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun rememberContrastLevel(context: android.content.Context, uiModeManager: UiModeManager): ContrastLevel {
  var contrast by remember { mutableFloatStateOf(uiModeManager.contrast) }
  DisposableEffect(uiModeManager) {
    val listener = UiModeManager.ContrastChangeListener { contrast = it }
    uiModeManager.addContrastChangeListener(context.mainExecutor, listener)
    onDispose { uiModeManager.removeContrastChangeListener(listener) }
  }
  return contrastLevelFor(contrast)
}

@Composable
fun RefittedTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val context = LocalContext.current
  val contrastLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    val uiModeManager = remember(context) { context.getSystemService(UiModeManager::class.java) }
    rememberContrastLevel(context, uiModeManager)
  } else {
    ContrastLevel.STANDARD
  }

  val colorScheme: ColorScheme
  val extendedColors: ExtendedColorScheme
  when (contrastLevel) {
    ContrastLevel.HIGH -> {
      colorScheme = if (darkTheme) darkHighContrastScheme else lightHighContrastScheme
      extendedColors = if (darkTheme) extendedDarkHighContrast else extendedLightHighContrast
    }
    ContrastLevel.MEDIUM -> {
      colorScheme = if (darkTheme) darkMediumContrastScheme else lightMediumContrastScheme
      extendedColors = if (darkTheme) extendedDarkMediumContrast else extendedLightMediumContrast
    }
    ContrastLevel.STANDARD -> {
      colorScheme = if (darkTheme) darkScheme else lightScheme
      extendedColors = if (darkTheme) extendedDark else extendedLight
    }
  }

  CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
    MaterialTheme(colorScheme = colorScheme, content = content)
  }
}

/**
 * TopAppBar fill: primary/onPrimary in light mode (already the vivid brand blue there), but
 * tertiaryContainer/onTertiaryContainer in dark mode - dark mode's own primary is a pale pastel
 * (by M3 convention, since primary is also used as a direct icon/text tint elsewhere and needs
 * to stay legible against the dark surface), which reads as washed-out for a bar this prominent.
 * tertiaryContainer isn't used anywhere else in the app, so this only affects the app bar, not
 * every other primary-tinted element. Verified contrast: onTertiaryContainer on tertiaryContainer
 * is 4.3:1 in the current dark palette.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarColors(): TopAppBarColors {
  val colorScheme = MaterialTheme.colorScheme
  return if (isSystemInDarkTheme()) {
    TopAppBarDefaults.topAppBarColors(
      containerColor = tertiaryLight,
      titleContentColor = onTertiaryLight,
      navigationIconContentColor = onTertiaryLight,
      actionIconContentColor = onTertiaryLight
    )
  } else {
    TopAppBarDefaults.topAppBarColors(
      containerColor = colorScheme.primary,
      titleContentColor = colorScheme.onPrimary,
      navigationIconContentColor = colorScheme.onPrimary,
      actionIconContentColor = colorScheme.onPrimary
    )
  }
}
