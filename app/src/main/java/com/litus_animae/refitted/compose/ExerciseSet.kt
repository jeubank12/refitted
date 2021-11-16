package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litus_animae.refitted.R
import com.litus_animae.refitted.models.ExerciseViewModel
import kotlinx.coroutines.FlowPreview

@FlowPreview
object ExerciseSet {

    @Composable
    fun ExerciseSetView(model: ExerciseViewModel = viewModel()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(3f)) {}
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    val enabled by model.canMoveLeft.collectAsState(false)
                    Button(
                        onClick = { model.moveLeft() },
                        enabled = enabled
                    ) {
                        val text = stringResource(id = R.string.move_left)
                        Text(text)
                    }
                }
                Column(Modifier.weight(3f)) {
                }
                Column(Modifier.weight(1f)) {
                    val enabled by model.canMoveRight.collectAsState(false)
                    Button(
                        onClick = { model.moveRight() },
                        enabled = enabled
                    ) {
                        val text = stringResource(id = R.string.move_right)
                        Text(text)
                    }
                }
            }
        }
    }
}