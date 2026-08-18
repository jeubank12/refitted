package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.state.Weight

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
      // TODO does this disrupt screen reader being able to see the contents?
      .clickable(onClickLabel = "edit") { onStartEditWeight(weight) }) {
    Icon(
      Icons.Rounded.Edit,
      contentDescription = "edit weight",
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
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )
    val displayWeight = String.format("%.1f", saveWeight)
    Text(
      "$displayWeight $weightUnit",
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )
  }
}