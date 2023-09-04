package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.state.Weight

@Composable
fun WeightDisplay(
  onStartEditWeight: (Weight) -> Unit,
  weight: Weight,
  saveWeight: Double
) {
  Column(
    Modifier
      .fillMaxWidth()
      .padding(5.dp)
      .clickable { onStartEditWeight(weight) }) {
    Icon(
      Icons.Rounded.Edit,
      contentDescription = null,
      Modifier.align(Alignment.End)
    )
  }
  Column(
    Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val weightLabel = stringResource(id = R.string.weight_label)
    val weightUnit = stringResource(id = R.string.lbs)
    Text(
      weightLabel,
      style = MaterialTheme.typography.h5,
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )
    val displayWeight = String.format("%.1f", saveWeight)
    Text(
      "$displayWeight $weightUnit",
      style = MaterialTheme.typography.h4,
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )
  }
}