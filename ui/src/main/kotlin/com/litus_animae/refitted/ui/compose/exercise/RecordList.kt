package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.LocalFeatures
import com.litus_animae.refitted.ui.compose.charts.BubbleChart
import com.litus_animae.refitted.ui.compose.charts.BubbleChartExploded
import com.litus_animae.refitted.ui.compose.charts.BubbleData
import com.litus_animae.refitted.ui.compose.charts.EffortChart
import com.litus_animae.refitted.ui.compose.charts.EffortPoint
import com.litus_animae.refitted.ui.compose.charts.LineChart
import com.litus_animae.refitted.ui.compose.state.SetHistory
import com.litus_animae.refitted.ui.compose.util.LoadingView
import com.litus_animae.refitted.identity.ConfigProvider
import com.litus_animae.refitted.data.effort.EffortModel
import com.litus_animae.refitted.data.effort.toEffortSet
import com.litus_animae.refitted.data.models.SetRecord
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SetRecordList(
  modifier: Modifier = Modifier,
  history: SetHistory
) {
  val records = history.paged.collectAsLazyPagingItems()

  Column(modifier
    .fillMaxSize()) {
    Row(
      Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colors.primary)
        .windowInsetsPadding(
          AppBarDefaults.topAppBarWindowInsets.union(
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
          )
        )
        .padding(start = 10.dp, bottom = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // TODO localize
      Text(
        "Set History", style = MaterialTheme.typography.h6, color = contentColorFor(
          backgroundColor = MaterialTheme.colors.primary
        )
      )
      IconButton({ records.refresh() }) {
        Icon(
          Icons.Default.Refresh,
          // TODO localize
          "refresh",
          tint = contentColorFor(backgroundColor = MaterialTheme.colors.primary)
        )
      }
    }

    LazyColumn(Modifier.weight(2f)) {

      // TODO does this cause everything to recompose? Should we just overlay?
      if (records.loadState.refresh is LoadState.Loading) {
        item {
          Row(Modifier.fillMaxWidth()) {
            LoadingView()
          }
        }
      } else {
        // TODO if we can pull the loading state out, then make this sticky at the top outside the lazycolumn
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
            RecordItem(dateFormat, record)
          }
        }
      }
    }

    val recent by history.recent.collectAsState(initial = emptyList())
    if (LocalFeatures.current.flags[ConfigProvider.Companion.Feature.RECORD_CHART_TYPE] == "effort") {
      // Fit from the bounded recent-history flow, never the paged snapshot backing the list
      // above - that would make the trend visibly shift as the user scrolls and more pages
      // load, since the fit would be over however much of the list happens to be loaded.
      if (recent.isNotEmpty()) {
        val series = remember(recent) { EffortModel.score(recent.map { it.toEffortSet() }) }
        val points = remember(series) {
          series.sets.map {
            EffortPoint(
              // Spreads same-day sets within [dayOffset, dayOffset + 1) instead of stacking
              // them on one x value.
              x = it.dayOffset + (it.setIndexInSession + 1f) / (it.setsInSession + 1f),
              weight = it.source.weight.toFloat(),
              size = it.size,
              zone = it.zone
            )
          }
        }
        val trend = remember(series) {
          listOf(series.trend.map { it.dayOffset.toFloat() to it.expectedWeight.toFloat() })
        }
        EffortChart(
          Modifier
            .fillMaxWidth()
            .weight(1f),
          points = points,
          trend = trend
        )
      }
    } else if (records.itemCount > 0) {
      val items = remember(records.itemSnapshotList) {
        records.itemSnapshotList.items.reversed()
      }
      if (LocalFeatures.current.flags[ConfigProvider.Companion.Feature.RECORD_CHART_TYPE] == "bubble-exploded") {
        val data = remember(items) {
          items.map { BubbleData(it.completed, it.weight.toFloat(), it.reps) }
        }

        BubbleChartExploded(
          Modifier
            .fillMaxWidth()
            .weight(1f),
          data = data,
          inverseRelationship = true
        )
      } else if (LocalFeatures.current.flags[ConfigProvider.Companion.Feature.RECORD_CHART_TYPE] == "bubble") {
        val data = remember(items) {
          items.map { BubbleData(it.completed, it.weight.toFloat(), it.reps) }
        }

        BubbleChart(
          Modifier
            .fillMaxWidth()
            .weight(1f),
          data = data,
          inverseRelationship = true
        )
      } else {
        val data = remember(items) {
          items.map { it.completed to it.weight.toFloat() }
        }
        LineChart(
          Modifier
            .fillMaxWidth()
            .weight(1f),
          data = data
        )
      }
    }
  }
}

@Composable
private fun RecordItem(
  dateFormat: DateTimeFormatter,
  record: SetRecord
) {
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

@Preview
@Composable
private fun PreviewSetRecordList() {
  val records = listOf(
    SetRecord(35.0, 6, "X", "Y", Instant.ofEpochMilli(1000), "Z"),
    SetRecord(37.5, 6, "X", "Y", Instant.ofEpochMilli(2000), "Z"),
    SetRecord(35.0, 10, "X", "Y", Instant.ofEpochMilli(4000), "Z"),
    SetRecord(40.0, 6, "X", "Y", Instant.ofEpochMilli(6000), "Z"),
    SetRecord(45.0, 2, "X", "Y", Instant.ofEpochMilli(7000), "Z"),
  )
  val data = PagingData.from(
    records,
    sourceLoadStates = LoadStates(
      LoadState.NotLoading(true),
      LoadState.NotLoading(true),
      LoadState.NotLoading(true)
    )
  )

  SetRecordList(
    Modifier
      .background(Color.White)
      .height(400.dp)
      .width(200.dp),
    SetHistory(flowOf(data), flowOf(records))
  )
}