package com.litus_animae.refitted.module

import com.litus_animae.refitted.data.device.WatchDevice
import com.litus_animae.refitted.data.device.WatchPlan
import com.litus_animae.refitted.data.device.WatchService
import com.litus_animae.refitted.data.device.WatchState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WatchServiceModule {

    @Binds
    @Singleton
    abstract fun bindWatchService(
        watchServiceImpl: DisabledWatchService
    ): WatchService
}

// Debug/minifiedDebug never call registerForAppEvents at all, on purpose: every Android build
// variant shares the same hardcoded watch-app UUID (GarminWatchService.REFITTED_WATCH_APP_ID,
// paired 1:1 with connectiq/manifest.xml's <iq:application id="...">). Per Garmin's own Mobile
// SDK for Android docs ("Receiving Messages"): "multiple companion apps cannot be registered to
// receive messages from the same ConnectIQ application. The SDK will override any previous
// registrations with each call to registerForAppEvents()." A debug build installed alongside
// release would silently steal or lose that single registration slot with no error on either
// side - see the full gotcha in garmin/CLAUDE.md. GarminConnection's SDK init/shutdown lifecycle
// still runs (it's injected directly in RefittedApplication, not through this interface) - only
// the per-device app-event listener registration is skipped.
@Singleton
class DisabledWatchService @Inject constructor() : WatchService {
    override val state: StateFlow<WatchState> = MutableStateFlow(WatchState.Unsupported)
    override val availableDevices: StateFlow<List<WatchDevice>> = MutableStateFlow(emptyList())
    override suspend fun refresh() {}
    override suspend fun selectDevice(deviceId: String) {}
    override suspend fun startSession(plan: WatchPlan): Result<Unit> =
        Result.failure(UnsupportedOperationException("watch sync is disabled in this build"))
    override suspend fun endSession() {}
}
