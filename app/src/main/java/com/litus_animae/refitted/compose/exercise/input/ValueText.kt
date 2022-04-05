package com.litus_animae.refitted.compose.exercise.input

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * @param displayedText Initial text to display
 * @param onEdit Input validation during editing
 * @param onEditComplete Input validation at end of editing
 * @param suffix Optional content to append to the text
 */
@Composable
fun ValueTextField(
  displayedText: String,
  onEdit: (String) -> String,
  onEditComplete: (String) -> String,
  fontSize: TextUnit = LocalTextStyle.current.fontSize,
  suffix: (@Composable () -> Unit)?
) {
  val focusManager = LocalFocusManager.current
  val (value, setValue) = remember(displayedText) { mutableStateOf(displayedText) }
  val alignment = if (suffix != null) TextAlign.End else TextAlign.Center
  BasicTextField(
    value = value,
    onValueChange = { setValue(onEdit(it)) },
    modifier = Modifier.padding(vertical = 10.dp),
    singleLine = true,
    textStyle = TextStyle(fontSize = fontSize, textAlign = alignment),
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Number,
      autoCorrect = false,
      imeAction = ImeAction.Done
    ),
    keyboardActions = KeyboardActions(onDone = {
      setValue(onEditComplete(displayedText))
      focusManager.clearFocus()
    })
  ) { content ->
    Row(
      Modifier
        .border(1.dp, Color.Black, GenericShape { size, _ ->
          val bezelLength = 10f
          moveTo(0f, size.height - bezelLength)
          lineTo(bezelLength, size.height)
          lineTo(size.width - bezelLength, size.height)
          lineTo(size.width, size.height - bezelLength)
          moveTo(0f, size.height - bezelLength)
        })
        .padding(bottom = 1.dp)
    ) {
      if (suffix != null) {
        Column(Modifier.weight(1f)) {
          content()
        }
        Column(
          Modifier
            .padding(start = 5.dp)
            .weight(1f)
        ) {
          suffix()
        }
      } else content()
    }
  }
}