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
@DynamoDBTable(tableName = "refitted-exercise")
class Exercise : Parcelable {
    @get:DynamoDBAttribute(attributeName = "Disc")
    @get:DynamoDBRangeKey(attributeName = "Disc")
    @ColumnInfo(name = "exercise_workout")
    var workout = ""

    @get:DynamoDBAttribute(attributeName = "Id")
    @get:DynamoDBHashKey(attributeName = "Id")
    @ColumnInfo(name = "exercise_name")
    var id = ""

    @get:DynamoDBAttribute(attributeName = "Note")
    var description: String? = null

    //private Date cacheDate;
    constructor() {}

    val name: String?
        get() = if (id.isEmpty() || !id.contains("_")) null else id.split("_".toRegex(), 2).toTypedArray()[1]

    fun getName(allowNull: Boolean): String? {
        if (allowNull) {
            return name
        }
        return if (id.isEmpty() || !id.contains("_")) "" else id.split("_".toRegex(), 2).toTypedArray()[1]
    }

    fun setName(name: String) {
        id = category + "_" + name
    }

    var category: String
        get() = id.split("_".toRegex(), 2).toTypedArray()[0]
        set(category) {
            id = category.split("_".toRegex(), 2).toTypedArray()[0] + "_" + getName(false)
        }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(workout)
        dest.writeString(id)
        dest.writeString(description)
    }

    protected constructor(parcel: Parcel) {
        workout = parcel.readString()!!
        id = parcel.readString()!!
        description = parcel.readString()
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