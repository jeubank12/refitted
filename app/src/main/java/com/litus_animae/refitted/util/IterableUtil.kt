package com.litus_animae.refitted.util

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