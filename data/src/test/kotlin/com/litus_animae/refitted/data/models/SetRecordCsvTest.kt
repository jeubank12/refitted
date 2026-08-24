package com.litus_animae.refitted.data.models

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.jupiter.api.Test

class SetRecordCsvTest {

  @Test
  fun `empty list is just the header`() {
    assertThat(emptyList<SetRecord>().toCsv()).isEqualTo("completed,exercise,weight,reps")
  }

  @Test
  fun `rows are sorted chronologically and include every field`() {
    val later = SetRecord(155.0, 12, "W", "T", Instant.parse("2026-08-24T13:39:00Z"), "Legs_Leg Press")
    val earlier = SetRecord(155.0, 12, "W", "T", Instant.parse("2026-08-24T13:37:00Z"), "Legs_Leg Press")

    val csv = listOf(later, earlier).toCsv()

    assertThat(csv).isEqualTo(
      """
      completed,exercise,weight,reps
      2026-08-24T13:37:00Z,Legs_Leg Press,155.0,12
      2026-08-24T13:39:00Z,Legs_Leg Press,155.0,12
      """.trimIndent()
    )
  }
}
