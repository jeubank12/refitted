package com.litus_animae.refitted

import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * A simple Activity for Hilt-based Compose UI tests.
 * Provides the necessary ViewModelStore and Hilt dependency injection.
 *
 * This activity is in the debug source set so it's included in the debug APK
 * (same process as the app), allowing instrumented tests to launch it.
 */
@AndroidEntryPoint
class HiltTestActivity : AppCompatActivity()
