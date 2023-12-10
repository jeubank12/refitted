package com.litus_animae.refitted.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "SavedState")
data class SavedState(
    @PrimaryKey
    val key: String,
    val value: String
)