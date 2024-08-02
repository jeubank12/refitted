package com.litus_animae.refitted

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import dagger.hilt.android.HiltAndroidApp


@HiltAndroidApp
class RefittedApplication : Application() {

    override fun onCreate() {

        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder()
                .detectAll() // or .detectAll() for all detectable problems
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyDeath()
                .build()
        )

        super.onCreate()
    }

    companion object {
        private const val TAG = "RefittedApplication"
    }
}