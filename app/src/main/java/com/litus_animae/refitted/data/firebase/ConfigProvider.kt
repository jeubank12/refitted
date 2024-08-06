package com.litus_animae.refitted.data.firebase

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.litus_animae.refitted.BuildConfig
import com.litus_animae.refitted.R
import com.litus_animae.refitted.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigProvider @Inject constructor(private val log: LogUtil) {
  companion object {
    private const val TAG = "ConfigProvider"

    enum class Feature(val flag: String) {
      RECORD_CHART_TYPE("record_chart_type")
    }

    private val featureKeys = Feature.entries.map { it.flag }.toSet()

    data class RemoteConfig(
      val flags: Map<Feature, FirebaseRemoteConfigValue> = emptyMap()
    )
  }

  private val _config by lazy {
    log.d(TAG, "initializing FirebaseConfig on ${Thread.currentThread().name}")
    val instance = Firebase.remoteConfig

    if (BuildConfig.DEBUG) {
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

  val currentConfig = callbackFlow {
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
                instance.getValue(
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
    instance.addOnConfigUpdateListener(listener)

    setup.await()
    log.d(TAG, "initializing config update on ${Thread.currentThread().name}")
    send(RemoteConfig(Feature.entries.associateWith { instance.getValue(it.flag) }))

    awaitClose {
      // there is no remove listener
    }
  }.flowOn(Dispatchers.IO)
}