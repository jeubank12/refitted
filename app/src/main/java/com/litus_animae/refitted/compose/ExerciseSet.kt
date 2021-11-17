package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.litus_animae.refitted.R

@Composable
fun ExerciseSetView(currentIndex: Int, maxIndex: Int, updateIndex: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(3f)) {}
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                val enabled = currentIndex > 0
                Button(
                    onClick = { updateIndex(currentIndex - 1) },
                    enabled = enabled
                ) {
                    val text = stringResource(id = R.string.move_left)
                    Text(text)
                }
            }
            Column(Modifier.weight(3f)) {
            }
            Column(Modifier.weight(1f)) {
                val enabled = currentIndex < maxIndex
                Button(
                    onClick = { updateIndex(currentIndex + 1) },
                    enabled = enabled
                ) {
                    val text = stringResource(id = R.string.move_right)
                    Text(text)
                }
            }
        }
    }
}
