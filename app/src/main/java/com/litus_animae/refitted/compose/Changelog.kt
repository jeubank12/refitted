package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.R

@Composable
fun Changelog(onDismiss: () -> Unit) {
  val versionHeader = stringResource(id = R.string.changelog_version_header)
  val changelog = stringArrayResource(id = R.array.changelog)
  val dismissText = stringArrayResource(id = R.array.dismiss_changelog).random()
  AlertDialog(onDismissRequest = {},
    title = { Text("Changelog") },
    text = {
      LazyColumn {
        itemsIndexed(changelog) { _, value ->
          if (value.startsWith(versionHeader)) Text(
            value.substring(2),
            fontWeight = FontWeight.Bold
          )
          else Text("\u2022 $value", Modifier.padding(start = 10.dp))
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss) {
        Text(dismissText)
      }
    })
}