package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlin.math.withSign

@Composable
fun WeightButtons(initialWeight: Double) {
    var weight by remember(initialWeight) {
        mutableStateOf(initialWeight)
    }
    Row {
        Column(Modifier.weight(3f)) {
            ButtonSet(-1) {
                if (it.sign < 0 && weight < it.absoluteValue) weight = 0.0
                else weight += it
            }
        }
        Column(
            Modifier
                .weight(2f)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End
        ) {
            Text(String.format("%.1f", weight))
        }
        Column(Modifier.weight(3f)) {
            ButtonSet(1) { weight += it }
        }
    }
}

@Composable
private fun ButtonSet(sign: Int, updateWeight: (change: Double) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        WeightButton(2.5.withSign(sign), updateWeight)
        WeightButton(5.0.withSign(sign), updateWeight)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        WeightButton(10.0.withSign(sign), updateWeight)
        WeightButton(25.0.withSign(sign), updateWeight)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        WeightButton(45.0.withSign(sign), updateWeight)
    }
}

@Composable
private fun WeightButton(weight: Double, onClick: (Double) -> Unit) {
    val sign = if (weight.sign < 0) "" else "+"
    val df = DecimalFormat.getInstance()
    if (df is DecimalFormat) {
        df.applyPattern("$sign##.#")
    }
    Button(onClick = { onClick(weight) }) {
        Text(df.format(weight))
    }
}

@Composable
@Preview(heightDp = 300)
fun PreviewWeightButtons() {
    MaterialTheme(Theme.lightColors) {
        Column(Modifier.fillMaxSize()) {
            WeightButtons(10.5)
        }
    }
}