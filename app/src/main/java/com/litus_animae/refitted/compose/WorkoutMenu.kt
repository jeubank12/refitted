package com.litus_animae.refitted.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
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
import androidx.paging.compose.itemsIndexed
import com.litus_animae.refitted.models.WorkoutPlan
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
        WorkoutPlanMenu(items = data) {}
    }
}

@Composable
fun WorkoutPlanMenu(
    items: LazyPagingItems<WorkoutPlan>,
    onSelect: (WorkoutPlan) -> Unit
) {
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
                    "Workouts", style = MaterialTheme.typography.h6, color = contentColorFor(
                        backgroundColor = MaterialTheme.colors.primary
                    )
                )
                Icon(
                    Icons.Default.Refresh,
                    // TODO localize
                    "menu",
                    modifier = Modifier
                        .clickable {
                            items.refresh()
                        }
                        .padding(start = 10.dp, end = 10.dp),
                    tint = contentColorFor(backgroundColor = MaterialTheme.colors.primary))
            }
        }

        if (items.loadState.refresh is LoadState.Loading) {
            item {
                Row(Modifier.fillMaxWidth()) {
                    LoadingView()
                }
            }
        } else {
            itemsIndexed(items) { _, plan ->
                if (plan != null) {
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
        }
    }
}