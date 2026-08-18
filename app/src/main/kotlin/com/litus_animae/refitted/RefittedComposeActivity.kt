package com.litus_animae.refitted

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litus_animae.refitted.ui.compose.LocalFeatures
import com.litus_animae.refitted.ui.compose.Top
import com.litus_animae.refitted.identity.ConfigProvider
import com.litus_animae.refitted.ui.models.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@FlowPreview
@AndroidEntryPoint
class RefittedComposeActivity : AppCompatActivity() {
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // this is slow still
    enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT))

    setContent {
      val userModel: UserViewModel = hiltViewModel()
      val config by userModel.featureFlags.collectAsStateWithLifecycle(initialValue = ConfigProvider.Companion.RemoteConfig())

      // Each Top() destination applies its own theme during the M2->M3 migration
      // (ui/CLAUDE.md); this wrap moves back up here once every screen is on M3.
      CompositionLocalProvider(LocalFeatures provides config) {
        Top()
      }
    }
  }
}