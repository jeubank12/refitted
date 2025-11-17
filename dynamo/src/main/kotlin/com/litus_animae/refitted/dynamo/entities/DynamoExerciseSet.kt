package com.litus_animae.refitted.dynamo.entities

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.*
import com.litus_animae.refitted.data.models.ExerciseSet
import kotlinx.coroutines.flow.flowOf

/**
 * DynamoDB entity for ExerciseSet persistence.
 * Domain code should use the corresponding model from :data instead.
 */
@DynamoDBTable(tableName = "refitted-exercise")
data class DynamoExerciseSet @JvmOverloads constructor(
  @get:DynamoDBIndexHashKey(attributeName = "Disc", globalSecondaryIndexName = "Reverse-index")
  @get:DynamoDBAttribute(attributeName = "Disc")
  @get:DynamoDBRangeKey(attributeName = "Disc")
  var workout: String = "",

  @get:DynamoDBIndexRangeKey(attributeName = "Id", globalSecondaryIndexName = "Reverse-index")
  @get:DynamoDBAttribute(attributeName = "Id")
  @get:DynamoDBHashKey(attributeName = "Id")
  var id: String = "",

  @get:DynamoDBAttribute(attributeName = "Name")
  var name: String = "",

  @get:DynamoDBAttribute(attributeName = "Note")
  var note: String = "",

  @get:DynamoDBAttribute(attributeName = "Reps")
  var reps: Int = 0,

  @get:DynamoDBAttribute(attributeName = "Sets")
  var sets: Int = 0,

  @get:DynamoDBAttribute(attributeName = "ToFailure")
  var isToFailure: Boolean = false,

  @get:DynamoDBAttribute(attributeName = "Rest")
  var rest: Int = 0,

  @get:DynamoDBAttribute(attributeName = "RepsUnit")
  var repsUnit: String = "",

  @get:DynamoDBAttribute(attributeName = "RepsRange")
  var repsRange: Int = 0,

  @get:DynamoDBAttribute(attributeName = "TimeLimit")
  var timeLimit: Int? = null,

  @get:DynamoDBAttribute(attributeName = "TimeLimitUnit")
  var timeLimitUnit: String? = null,

  @get:DynamoDBAttribute(attributeName = "RepsSequence")
  var repsSequence: String = ""
) {
    /**
     * Convert DynamoDB entity to domain model
     */
    fun toDomain(): ExerciseSet {
        val (day, step) = parseId(id)
        return ExerciseSet(
            workout = workout,
            day = day,
            step = step,
            name = name,
            note = note,
            reps = reps,
            sets = sets,
            isToFailure = isToFailure,
            rest = rest,
            repsUnit = repsUnit,
            repsRange = repsRange,
            timeLimit = timeLimit,
            timeLimitUnit = timeLimitUnit,
            repsSequence = repsSequence.split(',').mapNotNull { it.toIntOrNull() },
            exercise = flowOf(null) // Exercise lookup handled separately
        )
    }

    companion object {
        /**
         * Parse day and step from id field.
         * ID format: "{day}.{step}" where step can be multi-part like "2.1" or "2.a"
         * Examples:
         * - "1.2" -> ("1", "2")
         * - "1.2.1" -> ("1", "2.1")
         * - "1.2.a" -> ("1", "2.a")
         */
        private fun parseId(id: String): Pair<String, String> {
            val parts = id.split(".", limit = 2)
            return if (parts.size == 2) {
                Pair(parts[0], parts[1])
            } else {
                Pair(id, "")
            }
        }
    }
}
