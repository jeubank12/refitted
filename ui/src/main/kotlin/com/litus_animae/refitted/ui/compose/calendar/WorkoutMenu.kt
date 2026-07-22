package com.litus_animae.refitted.ui.compose.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.litus_animae.refitted.ui.compose.util.LoadingView
import com.litus_animae.refitted.ui.compose.util.Theme
import com.litus_animae.refitted.data.models.WorkoutPlan
import kotlinx.coroutines.flow.flowOf

@Preview(showBackground = true, widthDp = 200, heightDp = 400)
@Composable
fun WorkoutPlanPreview() {
  val data = flowOf(
    PagingData.from(
      listOf(
        WorkoutPlan("The first workout"),
        WorkoutPlan("The second workout"),
        WorkoutPlan("The third workout"),
        WorkoutPlan("The fourth workout"),
        WorkoutPlan("The fifth workout"),
        WorkoutPlan("The sixth workout")
      )
    )
  )
    .collectAsLazyPagingItems()
  MaterialTheme(Theme.darkColors) {
    Column {
      WorkoutPlanMenu(lastRefresh = "Refreshed At", plans = data, workoutPlanError = null, onSelect = {})
    }
  }
}

@Composable
fun ColumnScope.WorkoutPlanMenu(
  modifier: Modifier = Modifier,
  lastRefresh: String,
  plans: LazyPagingItems<WorkoutPlan>,
  workoutPlanError: String?,
  onSelect: (WorkoutPlan) -> Unit,
  onCreateCustom: () -> Unit = {}
) {
  LazyColumn(modifier) {
    item {
      Row(
        Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colors.primary)
          .windowInsetsPadding(
            WindowInsets.systemBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
          )
          .padding(start = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            // TODO localize
            Text(
              "Workouts", style = MaterialTheme.typography.h6, color = contentColorFor(
                backgroundColor = MaterialTheme.colors.primary
              )
            )
            IconButton(onClick = { plans.refresh() }) {
              Icon(
                Icons.Default.Refresh,
                // TODO localize
                "refresh",
                tint = contentColorFor(backgroundColor = MaterialTheme.colors.primary)
              )
            }
          }
          Row {
            Text(
              "Last Refreshed At: $lastRefresh", color = contentColorFor(
                backgroundColor = MaterialTheme.colors.primary
              )
            )
          }
        }
      }
    }

    if (workoutPlanError != null) {
      // FIXME cannot refresh once error is set
      item {
        Text(
          workoutPlanError,
          Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 15.dp, bottom = 15.dp),
          style = MaterialTheme.typography.button
        )
      }
    } else if (plans.loadState.refresh is LoadState.Loading) {
      item {
        Column(
          Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
        ) {
          Row(Modifier.align(Alignment.CenterHorizontally)) {
            LoadingView()
          }
        }
      }
    } else {
      items(
        count = plans.itemCount,
        key = plans.itemKey { it.workout }
      ) { index ->
        val plan = plans[index]
        if (plan != null) {
          // Custom plans sort after server plans (see WorkoutPlanDao.pagingSource) - this is
          // the transition into that section, so it only ever renders once.
          val previousPlan = if (index > 0) plans[index - 1] else null
          if (plan.isCustom && previousPlan?.isCustom != true) {
            // TODO localize
            Text(
              "CUSTOM WORKOUTS",
              Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 18.dp, bottom = 4.dp),
              style = MaterialTheme.typography.overline
            )
          }
          Text(
            plan.workout,
            Modifier
              .fillMaxWidth()
              .clickable { onSelect(plan) }
              .padding(start = 10.dp, top = 15.dp, bottom = 15.dp),
            style = MaterialTheme.typography.button
          )
          Divider()
        }
      }
      item {
        Row(
          Modifier
            .fillMaxWidth()
            .clickable { onCreateCustom() }
            .padding(start = 10.dp, end = 10.dp, top = 15.dp, bottom = 15.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Add, "create your own", tint = MaterialTheme.colors.primary)
          Spacer(Modifier.width(8.dp))
          // TODO localize
          Text(
            "Create your own",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.button
          )
        }
        Divider()
      }
    }
  }
}