package com.litus_animae.refitted.data.room

import androidx.room.TypeConverter
import java.time.Instant

object Converters {
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