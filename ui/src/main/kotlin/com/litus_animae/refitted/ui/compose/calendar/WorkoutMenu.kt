package com.litus_animae.refitted.ui.compose.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.litus_animae.refitted.ui.compose.util.LoadingView
import com.litus_animae.refitted.ui.compose.util.RefittedTheme
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
  RefittedTheme(darkTheme = true) {
    Column {
      WorkoutPlanMenu(
        lastRefresh = "Refreshed At",
        plans = data,
        workoutPlanError = null,
        selectedWorkoutName = "The second workout",
        onSelect = {}
      )
    }
  }
}

@Composable
fun ColumnScope.WorkoutPlanMenu(
  modifier: Modifier = Modifier,
  // Null when already shown elsewhere (e.g. the pane's own TopAppBar) - suppresses this row
  // entirely rather than showing a redundant copy.
  lastRefresh: String?,
  plans: LazyPagingItems<WorkoutPlan>,
  workoutPlanError: String?,
  // The currently loaded plan (Calendar's WorkoutViewModel.currentWorkout) - highlighted so it
  // stays identifiable in the Medium+ layout, where this list sits alongside its detail pane
  // rather than being dismissed on selection like at Compact width.
  selectedWorkoutName: String?,
  onRefresh: (() -> Unit)? = null,
  onSelect: (WorkoutPlan) -> Unit,
  onCreateCustom: () -> Unit = {},
  onRenameRequest: (WorkoutPlan) -> Unit = {}
) {
  LazyColumn(modifier) {
    if (lastRefresh != null) {
      item {
        Row(
          Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
              WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
            )
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            "Last Refreshed At: $lastRefresh",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
              // TODO localize
              Icon(Icons.Default.Refresh, "refresh")
            }
          }
        }
      }
      item { HorizontalDivider() }
    }

    if (workoutPlanError != null) {
      // FIXME cannot refresh once error is set
      item {
        Text(
          workoutPlanError,
          Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
              WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
            )
            .padding(start = 10.dp, top = 15.dp, bottom = 15.dp),
          style = MaterialTheme.typography.labelLarge
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
          // Custom plans sort after server plans (see WorkoutPlanDao.pagingSource) - these two
          // checks are each the transition into their section, so they only ever render once.
          val previousPlan = if (index > 0) plans[index - 1] else null
          if (!plan.isCustom && previousPlan == null) {
            // TODO localize
            Text(
              "FEATURED WORKOUTS",
              Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                  WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                )
                .padding(start = 10.dp, top = 10.dp, bottom = 4.dp),
              style = MaterialTheme.typography.labelSmall
            )
          }
          if (plan.isCustom && previousPlan?.isCustom != true) {
            // TODO localize
            Text(
              "CUSTOM WORKOUTS",
              Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                  WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                )
                .padding(start = 10.dp, top = 18.dp, bottom = 4.dp),
              style = MaterialTheme.typography.labelSmall
            )
          }
          WorkoutPlanRow(
            Modifier
              .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
              ),
            plan = plan,
            isSelected = plan.workout == selectedWorkoutName,
            onSelect = onSelect,
            onRenameRequest = onRenameRequest
          )
          HorizontalDivider()
        }
      }
      item {
        Row(
          Modifier
            .fillMaxWidth()
            .clickable { onCreateCustom() }
            .windowInsetsPadding(
              WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
            )
            .padding(start = 10.dp, end = 10.dp, top = 15.dp, bottom = 15.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Add, "create your own plan", tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(8.dp))
          // TODO localize
          Text(
            "Create your own plan",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge
          )
        }
        HorizontalDivider()
      }
    }
  }
}

/**
 * A server plan row is just a click target. A custom plan row additionally gets a trailing
 * pencil button - a visible, discoverable affordance beats a hidden gesture (long-press and
 * swipe were both tried here first and dropped: neither was discoverable, and a real swipe
 * gesture should act on release rather than just reveal a button). The pencil opens
 * [RenamePlanDialog], which now holds both Rename and Delete.
 */
@Composable
private fun WorkoutPlanRow(
  modifier: Modifier = Modifier,
  plan: WorkoutPlan,
  isSelected: Boolean,
  onSelect: (WorkoutPlan) -> Unit,
  onRenameRequest: (WorkoutPlan) -> Unit
) {
  Row(
    modifier
      .fillMaxWidth()
      .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
      .clickable { onSelect(plan) },
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (isSelected) {
      Icon(
        Icons.Default.Check,
        // TODO localize
        "currently selected plan",
        Modifier.padding(start = 10.dp),
        tint = MaterialTheme.colorScheme.onSecondaryContainer
      )
    }
    Text(
      plan.workout,
      Modifier
        .weight(1f)
        .padding(
          start = if (isSelected) 8.dp else 10.dp,
          top = 15.dp,
          bottom = 15.dp
        ),
      color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else Color.Unspecified,
      fontWeight = if (isSelected) FontWeight.Bold else null,
      style = MaterialTheme.typography.labelLarge
    )
    if (plan.isCustom) {
      IconButton(onClick = { onRenameRequest(plan) }) {
        // TODO localize
        Icon(Icons.Default.Edit, "rename or delete plan", tint = MaterialTheme.colorScheme.primary)
      }
    }
  }
}