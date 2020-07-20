package com.litus_animae.refitted.models

import android.os.Parcel
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable

@Entity(primaryKeys = ["exercise_name", "exercise_workout"])
data class Exercise(
        @ColumnInfo(name = "exercise_workout")
        val workout: String,

        @ColumnInfo(name = "exercise_name")
        val id: String,

        val description: String? = null
) : Parcelable {

    constructor(parcel: Parcel): this(
        workout = parcel.readString()!!,
        id = parcel.readString()!!,
        description = parcel.readString()
    )

    constructor(mutableExercise: MutableExercise):
            this(mutableExercise.workout, mutableExercise.id, mutableExercise.description)

    constructor(workout: String, id: String) : this(workout, id, description = null)

    val name: String?
        get() = if (id.isEmpty() || !id.contains("_")) null else id.split("_".toRegex(), 2).toTypedArray()[1]

    fun getName(allowNull: Boolean): String? {
        if (allowNull) {
            return name
        }
        return if (id.isEmpty() || !id.contains("_")) "" else id.split("_".toRegex(), 2).toTypedArray()[1]
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(workout)
        dest.writeString(id)
        dest.writeString(description)
    }

    companion object CREATOR : Parcelable.Creator<Exercise> {
        override fun createFromParcel(parcel: Parcel): Exercise {
            return Exercise(parcel)
        }

        override fun newArray(size: Int): Array<Exercise?> {
            return arrayOfNulls(size)
        }
    }
}