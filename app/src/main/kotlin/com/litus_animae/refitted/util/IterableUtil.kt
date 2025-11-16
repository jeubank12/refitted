package com.litus_animae.refitted.util

/**
 * Consumes an iterable and applies transforms on pairs of elements
 * The last element will be called with null as the next element
 *
 * Will not terminate for infinite sequences
 */
inline fun <T, R> Iterable<T>.maybeZipWithNext(transform: (a: T, b: T?) -> R): List<R> {
  val iterator = iterator()
  if (!iterator.hasNext()) return emptyList()
  val result = mutableListOf<R>()
  var current = iterator.next()
  while (iterator.hasNext()) {
    val next = iterator.next()
    result.add(transform(current, next))
    current = next
  }
  result.add(transform(current, null))
  return result
}

/**
 * Consumes an iterable and applies transforms on pairs of elements
 * The first element will be called with null as the previous element
 *
 * Will not terminate for infinite sequences
 */
inline fun <T, R> Iterable<T>.maybeZipWithPrevious(transform: (previous: T?, current: T) -> R): List<R> {
  val iterator = iterator()
  if (!iterator.hasNext()) return emptyList()
  val result = mutableListOf<R>()
  var current = iterator.next()
  result.add(transform(null, current))
  while (iterator.hasNext()) {
    val next = iterator.next()
    result.add(transform(current, next))
    current = next
  }
  return result
}

/**
 * Consumes a sequence and applies transforms on each element and the result of the previous transform
 */
inline fun <T, R> Sequence<T>.progressiveZipWithPrevious(crossinline transform: (previous: R?, current: T) -> R): Sequence<R> {
  return sequence {
    var current: R? = null
    for (element in this@progressiveZipWithPrevious) {
      val next = transform(current, element)
      yield(next)
      current = next
    }
  }
}

/**
 * Consumes an iterable and determines the minimum and maximum values
 *
 * Will not terminate for an infinite sequence
 *
 * @return Pair of minimum and maximum values. The first value is always less than or equal to the second value
 */
inline fun <T, R : Comparable<R>> Iterable<T>.rangeOf(transform: (a: T) -> R): Pair<R, R> {
  val iterator = iterator()
  if (!iterator.hasNext()) throw IllegalArgumentException("empty iterable provided")

  var current = iterator.next()
  var min = transform(current)
  var max = min
  while (iterator.hasNext()) {
    current = iterator.next()
    val value = transform(current)
    if (value < min) min = value
    if (value > max) max = value
  }
  return min to max
}

