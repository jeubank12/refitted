package com.litus_animae.refitted.compose

import androidx.compose.runtime.compositionLocalOf
import com.litus_animae.refitted.identity.ConfigProvider

val LocalFeatures = compositionLocalOf { ConfigProvider.Companion.RemoteConfig() }
