package com.litus_animae.refitted.models

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/**
 * A simple, stateless data container that holds all information for a given exercise set,
 * including its most recent performance and today's performances.
 */
data class ExerciseRecord(
  val targetSet: ExerciseSet,
  val latestRecord: Record?,
  val todaysRecords: List<Record>,
  val allSets: Flow<PagingData<SetRecord>>
)
