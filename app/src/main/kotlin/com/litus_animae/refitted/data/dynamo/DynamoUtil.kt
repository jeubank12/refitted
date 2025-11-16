package com.litus_animae.refitted.data.dynamo

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.Condition

object DynamoUtil {
  fun <T> DynamoDBMapper.queryReverseIndex(
    clazz: Class<T>,
    hashValue: T,
    rangeValue: String,
    rangeOperator: ComparisonOperator = ComparisonOperator.EQ
  ): Iterable<T> {
    val rangeCondition = Condition()
      .withComparisonOperator(rangeOperator)
      .withAttributeValueList(AttributeValue().withS(rangeValue))
    val queryExpression = DynamoDBQueryExpression<T>()
      .withHashKeyValues(hashValue)
      .withIndexName("Reverse-index")
      .withRangeKeyCondition("Id", rangeCondition)
      .withConsistentRead(false)
    return this.query(clazz, queryExpression)
  }
}