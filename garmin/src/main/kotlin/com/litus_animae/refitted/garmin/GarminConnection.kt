package com.litus_animae.refitted.garmin

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.garmin.android.connectiq.ConnectIQ
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Connect IQ SDK's process-wide binding. Debug builds enable
 * `VmPolicy.detectLeakedClosableObjects().penaltyDeath()`, so `initialize`/`shutdown` must be
 * paired exactly once per foreground/background transition - hence the [DefaultLifecycleObserver]
 * hooked to [androidx.lifecycle.ProcessLifecycleOwner] rather than any per-Activity lifecycle.
 */
@Singleton
class GarminConnection @Inject constructor(
  @ApplicationContext private val context: Context
) : DefaultLifecycleObserver {

  val connectIQ: ConnectIQ by lazy { ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS) }

  private var sdkReady = false
  private val readyListeners = mutableListOf<() -> Unit>()

  /** Set by a service while a watch session is active, so [onStop] does not tear the SDK down mid-session. */
  var sessionActive: Boolean = false

  fun whenReady(block: () -> Unit) {
    if (sdkReady) block() else readyListeners.add(block)
  }

  override fun onStart(owner: LifecycleOwner) {
    connectIQ.initialize(context, false, object : ConnectIQ.ConnectIQListener {
      override fun onSdkReady() {
        sdkReady = true
        readyListeners.forEach { it() }
        readyListeners.clear()
      }

      override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
        sdkReady = false
      }

      override fun onSdkShutDown() {
        sdkReady = false
      }
    })
  }

  override fun onStop(owner: LifecycleOwner) {
    if (sessionActive) return
    if (sdkReady) {
      connectIQ.shutdown(context)
    }
    sdkReady = false
  }
}
