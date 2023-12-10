package com.litus_animae.refitted

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RefittedApplication : Application() {

    companion object {
        private const val TAG = "RefittedApplication"
    }
}