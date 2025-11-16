package com.litus_animae.refitted.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IterableUtilTest {

  @Test
  fun `maybeZipWithNext with an empty list`() {
    // Verify that providing an empty iterable results in an empty list.
    val result = emptyList<Int>().maybeZipWithNext { _, _ -> "" }
    assertTrue(result.isEmpty())
  }

  @Test
  fun `maybeZipWithNext with a single element list`() {
    // Ensure the transform function is called once with the element and null as the 'next' value.
    val result = listOf(1).maybeZipWithNext { a, b -> a to b }
    assertEquals(listOf(1 to null), result)
  }

  @Test
  fun `maybeZipWithNext with multiple elements`() {
    // Test with a list containing multiple elements to ensure correct pairing of (current, next) and that the last element is paired with null.
    val result = listOf(1, 2, 3).maybeZipWithNext { a, b -> a to b }
    assertEquals(listOf(1 to 2, 2 to 3, 3 to null), result)
  }

  @Test
  fun `maybeZipWithNext with different data types`() {
    // Check that the function works correctly when the input type T and output type R are different.
    val result = listOf(1, 2, 3).maybeZipWithNext { a, b -> "$a and $b" }
    assertEquals(listOf("1 and 2", "2 and 3", "3 and null"), result)
  }

  @Test
  fun `maybeZipWithNext with a list containing nulls`() {
    // Verify correct behavior when the input iterable itself contains null values.
    val result = listOf(1, null, 3).maybeZipWithNext { a, b -> a to b }
    assertEquals(listOf(1 to null, null to 3, 3 to null), result)
  }

  @Test
  fun `maybeZipWithNext with a large list`() {
    // Test for performance and correctness with a very large list to check for any memory or performance issues.
    val largeList = (1..10000).toList()
    val result = largeList.maybeZipWithNext { a, b -> a to b }
    assertEquals(10000, result.size)
    assertEquals(1 to 2, result.first())
    assertEquals(10000 to null, result.last())
  }

  @Test
  fun `maybeZipWithPrevious with an empty list`() {
    // Verify that providing an empty iterable results in an empty list.
    val result = emptyList<Int>().maybeZipWithPrevious { _, _ -> "" }
    assertTrue(result.isEmpty())
  }

  @Test
  fun `maybeZipWithPrevious with a single element list`() {
    // Ensure the transform function is called once with null as the 'previous' value and the single element.
    val result = listOf(1).maybeZipWithPrevious { a, b -> a to b }
    assertEquals(listOf(null to 1), result)
  }

  @Test
  fun `maybeZipWithPrevious with multiple elements`() {
    // Test with a list containing multiple elements to ensure correct pairing of (previous, current), starting with (null, firstElement).
    val result = listOf(1, 2, 3).maybeZipWithPrevious { a, b -> a to b }
    assertEquals(listOf(null to 1, 1 to 2, 2 to 3), result)
  }

  @Test
  fun `maybeZipWithPrevious with different data types`() {
    // Check that the function works correctly when the input type T and output type R are different.
    val result = listOf(1, 2, 3).maybeZipWithPrevious { a, b -> "$a and $b" }
    assertEquals(listOf("null and 1", "1 and 2", "2 and 3"), result)
  }

  @Test
  fun `maybeZipWithPrevious with a list containing nulls`() {
    // Verify correct behavior when the input iterable itself contains null values.
    val result = listOf(1, null, 3).maybeZipWithPrevious { a, b -> a to b }
    assertEquals(listOf(null to 1, 1 to null, null to 3), result)
  }

  @Test
  fun `maybeZipWithPrevious with a large list`() {
    // Test for performance and correctness with a very large list to check for potential issues.
    val largeList = (1..10000).toList()
    val result = largeList.maybeZipWithPrevious { a, b -> a to b }
    assertEquals(10000, result.size)
    assertEquals(null to 1, result.first())
    assertEquals(9999 to 10000, result.last())
  }

  @Test
  fun `progressiveZipWithPrevious with an empty sequence`() {
    // Confirm that an empty input sequence produces an empty output sequence.
    val result =
      emptySequence<Int>().progressiveZipWithPrevious<Int, String> { _, _ -> "" }.toList()
    assertTrue(result.isEmpty())
  }

  @Test
  fun `progressiveZipWithPrevious with a single element sequence`() {
    // Check that the transform is called with a null 'previous' value and the single element, yielding one result.
    val result =
      sequenceOf(1).progressiveZipWithPrevious<Int, Int> { previous, current -> (previous ?: 0) + current }
        .toList()
    assertEquals(listOf(1), result)
  }

  @Test
  fun `progressiveZipWithPrevious with multiple elements`() {
    // Verify that each element is transformed using the result of the previous transformation as the 'previous' value.
    val result = sequenceOf(1, 2, 3).progressiveZipWithPrevious<Int, Int> { previous, current ->
      (previous ?: 0) + current
    }.toList()
    assertEquals(listOf(1, 3, 6), result)
  }

  @Test
  fun `progressiveZipWithPrevious with lazy evaluation`() {
    // Ensure the sequence is evaluated lazily, meaning the transform is not called until the sequence is consumed.
    var transformCalls = 0
    val sequence = sequenceOf(1, 2, 3)
    val resultSequence = sequence.progressiveZipWithPrevious<Int, String> { _, _ ->
      transformCalls++
      "dummy"
    }
    assertEquals(0, transformCalls)
    resultSequence.toList() // Consume the sequence
    assertEquals(3, transformCalls)
  }

  @Test
  fun `progressiveZipWithPrevious with a transform that returns null`() {
    // Test how the function behaves when the transform function returns a null value, which is then passed as the 'previous' value in the next step.
    val result = sequenceOf(1, 2, 3).progressiveZipWithPrevious<Int, Int?> { previous, current ->
      if (current == 2) null else (previous ?: 0) + current
    }.toList()
    assertEquals(listOf(1, null, 3), result)
  }

  @Test
  fun `progressiveZipWithPrevious with an infinite sequence`() {
    // Test that the function can handle an infinite sequence without crashing, by taking a limited number of elements from the result.
    val infiniteSequence = generateSequence(1) { it + 1 }
    val result =
      infiniteSequence.progressiveZipWithPrevious<Int, Int> { previous, current -> (previous ?: 0) + current }
    val taken = result.take(5).toList()
    assertEquals(listOf(1, 3, 6, 10, 15), taken)
  }

  @Test
  fun `rangeOf with an empty iterable`() {
    // Verify that an IllegalArgumentException is thrown when the input iterable is empty.
    assertThrows<IllegalArgumentException> {
      emptyList<Int>().rangeOf { it }
    }
  }

  @Test
  fun `rangeOf with a single element list`() {
    // Ensure that for a single item, the returned pair contains the same value for both min and max.
    val result = listOf(5).rangeOf { it }
    assertEquals(5 to 5, result)
  }

  @Test
  fun `rangeOf with all identical elements`() {
    // Check that if all elements produce the same comparable value, the min and max are both equal to that value.
    val result = listOf(5, 5, 5).rangeOf { it }
    assertEquals(5 to 5, result)
  }

  @Test
  fun `rangeOf with positive and negative numbers`() {
    // Test with a list of numbers including positive, negative, and zero to ensure min/max are correctly identified.
    val result = listOf(-1, 5, 0, -10, 10).rangeOf { it }
    assertEquals(-10 to 10, result)
  }

  @Test
  fun `rangeOf with pre sorted list`() {
    // Verify the function works correctly on a list that is already sorted in ascending order.
    val result = listOf(1, 2, 3, 4, 5).rangeOf { it }
    assertEquals(1 to 5, result)
  }

  @Test
  fun `rangeOf with reverse sorted list`() {
    // Verify the function works correctly on a list that is sorted in descending order.
    val result = listOf(5, 4, 3, 2, 1).rangeOf { it }
    assertEquals(1 to 5, result)
  }

  private data class MyComparable(val value: Int) : Comparable<MyComparable> {
    override fun compareTo(other: MyComparable) = value.compareTo(other.value)
  }

  @Test
  fun `rangeOf with custom comparable objects`() {
    // Test with a list of custom objects and a transform that returns a custom Comparable type to ensure generic constraints are met.
    val list = listOf(MyComparable(3), MyComparable(1), MyComparable(5))
    val result = list.rangeOf { it }
    assertEquals(MyComparable(1) to MyComparable(5), result)
  }

  @Test
  fun `rangeOf with boundary values`() {
    // Test with values at the boundaries of their data type (e.g., Int.MAX_VALUE, Int.MIN_VALUE) to check for correct comparison.
    val list = listOf(Int.MAX_VALUE, 0, Int.MIN_VALUE)
    val result = list.rangeOf { it }
    assertEquals(Int.MIN_VALUE to Int.MAX_VALUE, result)
  }
}
