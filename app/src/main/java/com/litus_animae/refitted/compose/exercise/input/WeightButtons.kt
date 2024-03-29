package com.litus_animae.refitted.compose.exercise.input

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import arrow.core.tail
import com.litus_animae.refitted.compose.state.Weight
import com.litus_animae.refitted.compose.util.ConstrainedButton
import com.litus_animae.refitted.compose.util.ConstrainedText
import com.litus_animae.refitted.compose.util.ConstrainedTextBox
import com.litus_animae.refitted.compose.util.Theme
import java.text.DecimalFormat
import kotlin.math.sign
import kotlin.math.withSign

@Composable
fun WeightButtons(weight: Weight) {
  val displayedWeight by weight.value
  Row {
    Column(Modifier.weight(3f)) {
      ButtonSet(-1, weight::plus)
    }
    Column(
      Modifier
        .weight(2f)
        .padding(horizontal = 5.dp)
        .padding(top = 16.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      val weightLabel = stringResource(id = com.litus_animae.refitted.R.string.weight_label)
      val weightUnit = stringResource(id = com.litus_animae.refitted.R.string.lbs)
      ConstrainedText(weightLabel)
      ConstrainedTextBox("FILL lbs") { _, fontSize ->
        ValueTextField(
          displayedText = formatWeightValue(displayedWeight),
          onEdit = { newValue ->
            newValue
              .replace(",", ".")
              .replace("^(\\d*\\.\\d).*".toRegex()) {
                it.groupValues.tail().firstOrNull() ?: ""
              }
          },
          onEditComplete = { value ->
            val updatedWeight = value.toDoubleOrNull() ?: 0.0
            weight.set(updatedWeight)
            formatWeightValue(updatedWeight)
          },
          fontSize = fontSize
        ) {
          Text(weightUnit, fontSize = fontSize)
        }
      }
    }
    Column(Modifier.weight(3f)) {
      ButtonSet(1, weight::plus)
    }
  }
}

private fun formatWeightValue(value: Double): String {
  return String.format("%.1f", value)
}

@Composable
private fun ButtonSet(sign: Int, updateWeight: (change: Double) -> Unit) {
  BoxWithConstraints {
    // TODO if available size is too small.....
    val maxButtonWidth = (maxWidth - 15.dp) / 2
    val maxButtonHeight = (maxHeight - 20.dp) / 3
    val availableSize = min(maxButtonWidth, maxButtonHeight)
    val buttonSize = min(availableSize, 50.dp)
    Column {
      Row(
        Modifier
          .fillMaxWidth()
          .padding(top = 5.dp),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        WeightButton(2.5.withSign(sign), updateWeight, buttonSize)
        WeightButton(5.0.withSign(sign), updateWeight, buttonSize)
      }
      Row(
        Modifier
          .fillMaxWidth()
          .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        WeightButton(10.0.withSign(sign), updateWeight, buttonSize)
        WeightButton(25.0.withSign(sign), updateWeight, buttonSize)
      }
      Row(
        Modifier
          .fillMaxWidth()
          .padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        WeightButton(45.0.withSign(sign), updateWeight, buttonSize)
      }
    }
  }
}

@Composable
private fun WeightButton(weight: Double, onClick: (Double) -> Unit, size: Dp) {
  val sign = if (weight.sign < 0) "" else "+"
  val df = DecimalFormat.getInstance()
  if (df is DecimalFormat) {
    df.applyPattern("$sign##.#")
  }
  ConstrainedButton(
    df.format(weight),
    onClick = { onClick(weight) },
    modifier = Modifier.size(size)
  )
}

@Composable
@Preview(heightDp = 300)
fun PreviewWeightButtons() {
  MaterialTheme(Theme.lightColors) {
    val weight = remember { Weight(10.5) }
    Column(Modifier.fillMaxSize()) {
      WeightButtons(weight)
    }
  }
}

@Composable
@Preview(heightDp = 150, widthDp = 250)
fun PreviewSmallWeightButtons() {
  MaterialTheme(Theme.lightColors) {
    val weight = remember { Weight(10.5) }
    Column(Modifier.fillMaxSize()) {
      WeightButtons(weight)
    }
  }
}