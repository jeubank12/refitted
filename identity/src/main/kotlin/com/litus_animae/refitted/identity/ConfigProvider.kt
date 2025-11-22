package com.litus_animae.refitted.identity

import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.litus_animae.refitted.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigProvider @Inject constructor(
  private val log: LogUtil,
  private val isDebug: Boolean,
) {
  companion object {
    private const val TAG = "ConfigProvider"

    enum class Feature(val flag: String) {
      RECORD_CHART_TYPE("record_chart_type")
    }

    private val featureKeys = Feature.entries.map { it.flag }.toSet()

    data class RemoteConfig(
      val flags: Map<Feature, String> = emptyMap()
    )
  }

  private val _config: Pair<FirebaseRemoteConfig, Task<Boolean?>> by lazy {
    log.d(TAG, "initializing FirebaseConfig on ${Thread.currentThread().name}")
    val instance = Firebase.remoteConfig

    if (isDebug) {
      val configSettings = remoteConfigSettings {
        minimumFetchIntervalInSeconds = 60
      }

      instance to instance.setConfigSettingsAsync(configSettings)
        .continueWithTask { instance.setDefaultsAsync(R.xml.remote_config_defaults) }
        .continueWithTask { instance.fetchAndActivate() }
    } else {
      instance to instance.setDefaultsAsync(R.xml.remote_config_defaults)
        .continueWithTask { instance.fetchAndActivate() }
    }
  }

  suspend fun config(): FirebaseRemoteConfig {
    val (instance, setup) = _config
    setup.await()
    return instance
  }

  val currentConfig: Flow<RemoteConfig> = callbackFlow {
    val (instance, setup) = _config

    log.d(TAG, "launching config flow on ${Thread.currentThread().name}")
    val listener = object : ConfigUpdateListener {
      override fun onUpdate(configUpdate: ConfigUpdate) {
        log.d(TAG, "processing config update on ${Thread.currentThread().name}")
        if (configUpdate.updatedKeys.any { featureKeys.contains(it) })
          instance.activate().addOnCompleteListener {
            if (it.isSuccessful && it.result) {
              log.d(TAG, "producing config update on ${Thread.currentThread().name}")
              trySend(RemoteConfig(Feature.entries.associateWith { feature ->
                instance.getString(
                  feature.flag
                )
              }))
            }
          }
      }

      override fun onError(error: FirebaseRemoteConfigException) {
        // do nothing
      }

    }
    setup.await()
    log.d(TAG, "initializing config update on ${Thread.currentThread().name}")
    send(RemoteConfig(Feature.entries.associateWith { instance.getString(it.flag) }))

    val registration = instance.addOnConfigUpdateListener(listener)

    awaitClose {
      log.d(TAG, "closing config update on ${Thread.currentThread().name}")
      registration.remove()
    }
  }.flowOn(Dispatchers.IO)
}