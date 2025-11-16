package com.litus_animae.refitted.util

import arrow.core.Option
import arrow.core.Some
import arrow.core.none

object MonadUtil {

  inline fun <A> optionWhen(a: Boolean, f: () -> A): Option<A> {
    return if (a) Some(f()) else none()
  }
}