package com.litus_animae.refitted.ui.compose.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
  onCreateCustom: () -> Unit = {},
  onRenameRequest: (WorkoutPlan) -> Unit = {},
  onDeleteRequest: (WorkoutPlan) -> Unit = {}
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
          WorkoutPlanRow(
            plan = plan,
            onSelect = onSelect,
            onRenameRequest = onRenameRequest,
            onDeleteRequest = onDeleteRequest
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
          Icon(Icons.Default.Add, "create your own plan", tint = MaterialTheme.colors.primary)
          Spacer(Modifier.width(8.dp))
          // TODO localize
          Text(
            "Create your own plan",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.button
          )
        }
        Divider()
      }
    }
  }
}

/**
 * A server plan row is just a click target. A custom plan row additionally supports long-press
 * (opens a Rename/Delete menu) and swipe (reveals a delete icon behind the row) - swipe is
 * delete-only, it never renames, and neither gesture deletes without going through the caller's
 * confirmation dialog first.
 */
@Composable
private fun WorkoutPlanRow(
  plan: WorkoutPlan,
  onSelect: (WorkoutPlan) -> Unit,
  onRenameRequest: (WorkoutPlan) -> Unit,
  onDeleteRequest: (WorkoutPlan) -> Unit
) {
  if (!plan.isCustom) {
    Text(
      plan.workout,
      Modifier
        .fillMaxWidth()
        .clickable { onSelect(plan) }
        .padding(start = 10.dp, top = 15.dp, bottom = 15.dp),
      style = MaterialTheme.typography.button
    )
    return
  }

  var showRowMenu by remember { mutableStateOf(false) }
  val density = LocalDensity.current
  val revealPx = remember(density) { with(density) { 72.dp.toPx() } }
  val offsetX = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()

  Box(Modifier.fillMaxWidth()) {
    Row(
      Modifier
        .matchParentSize()
        .background(MaterialTheme.colors.error),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = {
        scope.launch { offsetX.animateTo(0f) }
        onDeleteRequest(plan)
      }) {
        // TODO localize
        Icon(Icons.Default.Delete, "delete plan", tint = MaterialTheme.colors.onError)
      }
    }
    Box(
      Modifier
        .fillMaxWidth()
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .background(MaterialTheme.colors.surface)
        .draggable(
          orientation = Orientation.Horizontal,
          state = rememberDraggableState { delta ->
            scope.launch {
              offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
            }
          },
          onDragStopped = {
            offsetX.animateTo(if (offsetX.value < -revealPx / 2) -revealPx else 0f)
          }
        )
        .combinedClickable(
          onClick = { onSelect(plan) },
          onLongClick = { showRowMenu = true }
        )
    ) {
      Text(
        plan.workout,
        Modifier
          .fillMaxWidth()
          .padding(start = 10.dp, top = 15.dp, bottom = 15.dp),
        style = MaterialTheme.typography.button
      )
      DropdownMenu(expanded = showRowMenu, onDismissRequest = { showRowMenu = false }) {
        // TODO localize
        Text(
          "Rename",
          Modifier
            .fillMaxWidth()
            .clickable {
              showRowMenu = false
              onRenameRequest(plan)
            }
            .padding(start = 5.dp, end = 15.dp)
            .padding(vertical = 5.dp)
        )
        // TODO localize
        Text(
          "Delete",
          Modifier
            .fillMaxWidth()
            .clickable {
              showRowMenu = false
              onDeleteRequest(plan)
            }
            .padding(start = 5.dp, end = 15.dp)
            .padding(vertical = 5.dp)
        )
      }
    }
  }
}