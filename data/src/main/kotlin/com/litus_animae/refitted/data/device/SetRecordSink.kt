package com.litus_animae.refitted.data.device

import com.litus_animae.refitted.data.models.SetRecord

/**
 * Where a set completion lands regardless of its source (phone UI or watch). A singleton so a
 * watch listener - which runs outside any ViewModel's lifetime - has a writer available even when
 * no exercise screen is open.
 */
interface SetRecordSink {
  suspend fun store(records: List<SetRecord>)
}
