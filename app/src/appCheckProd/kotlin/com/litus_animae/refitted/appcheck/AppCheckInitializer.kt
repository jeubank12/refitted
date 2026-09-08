package com.litus_animae.refitted.appcheck

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object AppCheckInitializer {
  fun install() {
    FirebaseAppCheck.getInstance().apply {
      installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
      setTokenAutoRefreshEnabled(true)
    }
  }
}
