package com.litus_animae.refitted.appcheck

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object AppCheckInitializer {
  fun install() {
    FirebaseAppCheck.getInstance().apply {
      installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
      setTokenAutoRefreshEnabled(true)
    }
  }
}
