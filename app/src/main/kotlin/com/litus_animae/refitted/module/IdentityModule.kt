package com.litus_animae.refitted.module

import android.content.Context
import com.litus_animae.refitted.BuildConfig
import com.litus_animae.refitted.R
import com.litus_animae.refitted.identity.ConfigProvider
import com.litus_animae.refitted.util.LogUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IdentityModule {

  @Provides
  @Singleton
  fun provideConfigProvider(log: LogUtil): ConfigProvider {
    return ConfigProvider(
      log = log,
      isDebug = BuildConfig.DEBUG
    )
  }

  @Provides
  @Named("versionCode")
  fun provideVersionCode(): Int {
    return BuildConfig.VERSION_CODE
  }

  @Provides
  @Named("googleWebClientId")
  fun provideGoogleWebClientId(@ApplicationContext context: Context): String {
    return context.getString(R.string.default_web_client_id)
  }
}
