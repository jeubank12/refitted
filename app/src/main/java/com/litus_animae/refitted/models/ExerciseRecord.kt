package com.litus_animae.refitted.models

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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