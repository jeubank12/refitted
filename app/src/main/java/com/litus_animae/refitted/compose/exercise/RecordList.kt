package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.util.LoadingView
import com.litus_animae.refitted.models.SetRecord
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SetRecordList(flow: Flow<PagingData<SetRecord>>) {
  val records = flow.collectAsLazyPagingItems()
  LazyColumn {
    item {
      Row(
        Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colors.primary)
          .padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // TODO localize
        Text(
          "Set History", style = MaterialTheme.typography.h6, color = contentColorFor(
            backgroundColor = MaterialTheme.colors.primary
          )
        )
        Icon(
          Icons.Default.Refresh,
          // TODO localize
          "refresh",
          modifier = Modifier
            .clickable {
              records.refresh()
            }
            .padding(start = 10.dp, end = 10.dp),
          tint = contentColorFor(backgroundColor = MaterialTheme.colors.primary))
      }
    }

    if (records.loadState.refresh is LoadState.Loading) {
      item {
        Row(Modifier.fillMaxWidth()) {
          LoadingView()
        }
      }
    } else {
      val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
      item {
        Row(
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 15.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            stringResource(id = R.string.date),
            style = MaterialTheme.typography.button
          )
          Text(
            stringResource(id = R.string.reps_label),
            style = MaterialTheme.typography.button
          )
          val weightLabel = stringResource(id = R.string.weight_label)
          val weightUnits = stringResource(id = R.string.lbs)
          Text(
            "$weightLabel ($weightUnits)",
            style = MaterialTheme.typography.button
          )
        }
      }

      items(
        count = records.itemCount,
        key = records.itemKey { it.completed }
      ) { index ->
        val record = records[index]
        if (record != null) {
          Row(
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              dateFormat.format(record.completed),
              style = MaterialTheme.typography.button
            )
            Text(
              record.reps.toString(),
              style = MaterialTheme.typography.button
            )
            Text(
              String.format("%.1f", record.weight),
              style = MaterialTheme.typography.button
            )
          }
          Divider()
        }
      }
    }
  }
}