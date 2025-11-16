package com.litus_animae.refitted.compose.util

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Container which calculates the best text size based on available space
 *
 * @param textContent Content which will be passed through to the composable. Used
 * to calculate text size
 * @param content Content composable which takes the text content and font size
 * as parameters
 */
@Composable
fun ConstrainedTextBox(
  textContent: String,
  content: @Composable BoxWithConstraintsScope.(textContent: String, fontSize: TextUnit) -> Unit
) {
  BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    val availableWidth = maxWidth
    with(LocalDensity.current) {
      val maxSize = MaterialTheme.typography.button.fontSize
      val desiredSize = if (textContent.length >= 4) availableWidth.toSp() * 0.4f
      else if (textContent.length >= 3) availableWidth.toSp() * 0.6f
      else if (textContent.length >= 2) availableWidth.toSp() * 0.7f
      else availableWidth.toSp()
      val size = if (desiredSize > maxSize) maxSize else desiredSize
      content(textContent, size)
    }
  }
}

@Composable
fun ConstrainedText(
  textContent: String
) {
  BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    val availableWidth = maxWidth
    with(LocalDensity.current) {
      val maxSize = MaterialTheme.typography.button.fontSize
      val desiredSize = if (textContent.length >= 4) availableWidth.toSp() * 0.4f
      else if (textContent.length >= 3) availableWidth.toSp() * 0.6f
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

@Composable
fun ConstrainedButton(
  textContent: String,
  label: String = textContent,
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
    modifier = modifier.semantics { contentDescription = label }
  ) {
    ConstrainedText(textContent)
  }
}