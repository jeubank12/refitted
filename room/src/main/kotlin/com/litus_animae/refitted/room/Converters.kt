package com.litus_animae.refitted.room

import androidx.room.TypeConverter
import com.litus_animae.refitted.data.models.PlanKind
import java.time.Instant

object Converters {
  // Stored by name rather than ordinal so the migration default ('PROGRAM') stays legible in the
  // schema and reordering the enum can't silently remap existing rows.
  @JvmStatic
  @TypeConverter
  fun planKindFromString(value: String?): PlanKind = PlanKind.fromStored(value)

  @JvmStatic
  @TypeConverter
  fun planKindToString(kind: PlanKind): String = kind.name

  @JvmStatic
  @TypeConverter
  fun fromTimestamp(value: Long?): Instant? {
    return value?.let { Instant.ofEpochMilli(it) }
  }

  @JvmStatic
  @TypeConverter
  fun dateToTimestamp(instant: Instant?): Long? {
    return instant?.toEpochMilli()
  }

  @JvmStatic
  @TypeConverter
  fun intListFromString(value: String?): List<Int>? {
    return value?.split(",")
      ?.filter { it.isNotBlank() }
      ?.mapNotNull { it.toIntOrNull() }
  }

  @JvmStatic
  @TypeConverter
  fun intListToString(values: List<Int>?): String? {
    return values?.joinToString(",")
  }

  // FIXME this should be more elegant/safe
  @JvmStatic
  @TypeConverter
  fun stringListFromString(value: String?): List<String>? {
    return value?.split(",")?.filter{ it.isNotBlank() }
  }

  @JvmStatic
  @TypeConverter
  fun stringListToString(values: List<String>?): String? {
    return values?.joinToString(",")
  }
}