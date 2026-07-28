package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compact +/- stepper for editing a custom exercise's target (sets, reps, ...). A negative
 * [value] is the open/AMRAP state - not yet targeted; the first tap up starts it at 1 rather
 * than 0, since a 0-target isn't a meaningful prescription.
 *
 * [valueStyle]/[valueWidth] let a caller match the stepper's digits to a neighbouring number
 * (e.g. the reps NumberPicker) instead of always using the compact default; [showLabel] hides
 * the stepper's own caption when the surrounding layout already labels it.
 */
@Composable
fun TargetStepper(
  label: String,
  value: Int,
  onChange: (Int) -> Unit,
  valueStyle: TextStyle = MaterialTheme.typography.body2,
  valueWidth: Dp = 28.dp,
  showLabel: Boolean = true
) {
  val displayValue = value.takeIf { it >= 0 }
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = { onChange(((displayValue ?: 1) - 1).coerceAtLeast(1)) },
        enabled = displayValue != null && displayValue > 1
      ) {
        Icon(Icons.Default.Remove, contentDescription = "decrease $label")
      }
      // Fixed width so the digit count (1 vs 2 digits) doesn't shift the text off-centre
      // between the two fixed-size IconButtons.
      Text(
        displayValue?.toString() ?: "—",
        style = valueStyle,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(valueWidth)
      )
      IconButton(onClick = { onChange((displayValue ?: 0) + 1) }) {
        Icon(Icons.Default.Add, contentDescription = "increase $label")
      }
    }
    if (showLabel) {
      Text(label, style = MaterialTheme.typography.overline)
    }
  }
}
