package com.litus_animae.refitted

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import androidx.lifecycle.ProcessLifecycleOwner
import com.litus_animae.refitted.garmin.GarminConnection
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject


@HiltAndroidApp
class RefittedApplication : Application() {

  @Inject
  lateinit var garminConnection: GarminConnection

  override fun onCreate() {

    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(
        ThreadPolicy.Builder()
          .detectAll() // or .detectAll() for all detectable problems
          .penaltyLog()
          .build()
      )
      StrictMode.setVmPolicy(
        VmPolicy.Builder()
          .detectLeakedSqlLiteObjects()
          .detectLeakedClosableObjects()
          .penaltyLog()
          .penaltyDeath()
          .build()
      )
    }

    super.onCreate()

    // Hilt injects Application fields as part of super.onCreate(), so garminConnection is only
    // available after this point.
    ProcessLifecycleOwner.get().lifecycle.addObserver(garminConnection)
  }

  companion object {
    private const val TAG = "RefittedApplication"
  }
}