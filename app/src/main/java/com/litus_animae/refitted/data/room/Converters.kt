package com.litus_animae.refitted.data.room

import androidx.room.TypeConverter
import java.util.*

object Converters {
  @JvmStatic
  @TypeConverter
  fun fromTimestamp(value: Long?): Date? {
    return value?.let { Date(it) }
  }

  @JvmStatic
  @TypeConverter
  fun dateToTimestamp(date: Date?): Long? {
    return date?.time
  }

  @JvmStatic
  @TypeConverter
  fun intListFromString(value: String?): List<Int>? {
    return value?.split(",")?.mapNotNull { it.toIntOrNull() }
  }

  @JvmStatic
  @TypeConverter
  fun intListToString(values: List<Int>?): String? {
    return values?.joinToString(",")
  }
}