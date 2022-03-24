package com.litus_animae.refitted.compose.util

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun ConstrainedButton(
  textContent: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {},
  border: BorderStroke? = null,
  colors: ButtonColors = ButtonDefaults.buttonColors(),
  contentPadding: PaddingValues = PaddingValues(1.dp)
) {
  Button(
    onClick = onClick,
    border = border,
    colors = colors,
    contentPadding = contentPadding,
    modifier = modifier
  ) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      val availableWidth = maxWidth
      with(LocalDensity.current) {
        val maxSize = MaterialTheme.typography.button.fontSize
        val desiredSize = if (textContent.length >= 3) availableWidth.toSp() * 0.6f
        else if (textContent.length >= 2) availableWidth.toSp() * 0.7f
        else availableWidth.toSp()
        val size = if (desiredSize > maxSize) maxSize else desiredSize
        Text(
          textContent,
          fontSize = size
        )
      }
    }
  }
}