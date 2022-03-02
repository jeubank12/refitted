package com.litus_animae.refitted.compose

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemsIndexed
import com.litus_animae.refitted.models.WorkoutPlan

@Composable
fun WorkoutPlanMenu(
    items: LazyPagingItems<WorkoutPlan>
) {
    LazyColumn {
        item {
            // TODO header with hamburger and refresh
        }

        itemsIndexed(items) { _, plan ->
            if (plan != null && !plan.workout.isNullOrEmpty())
                Text(plan.workout)
        }
    }
}