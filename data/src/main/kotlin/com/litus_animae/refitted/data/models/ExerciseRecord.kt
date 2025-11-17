package com.litus_animae.refitted.data.models

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Domain model aggregating an exercise set with its historical records and pagination.
 */
data class ExerciseRecord(
  val targetSet: ExerciseSet,
  val defaultRecord: Record,
  val latestRecord: Flow<Record>,
  val allSets: Flow<PagingData<SetRecord>>,
  val currentRecords: Flow<List<Record>>
) {
  val currentRecordsCount = currentRecords.map { sets -> sets.size }

  override fun toString(): String {
    return "Record:$targetSet"
  }
}
