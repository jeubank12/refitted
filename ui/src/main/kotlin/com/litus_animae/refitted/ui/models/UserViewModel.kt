package com.litus_animae.refitted.ui.models

import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.identity.AuthProvider
import com.litus_animae.refitted.identity.ConfigProvider
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Named

@ExperimentalCoroutinesApi
@HiltViewModel
class UserViewModel @Inject constructor(
  private val log: LogUtil,
  private val savedStateRepo: SavedStateRepository,
  private val authProvider: AuthProvider,
  private val configProvider: ConfigProvider,
  @param:Named("versionCode") private val versionCode: Int,
  @param:Named("googleWebClientId") val googleWebClientId: String
) : ViewModel() {

  val userEmail: Flow<String?> =
    authProvider.currentUser
      .map { it?.email }
      .map {
        if (it.isNullOrBlank()) {
          null
        } else it
      }

  val userIsAdmin: Flow<Boolean> =
    authProvider.currentUser
      .map { it?.getIdToken(false)?.await() }
      .map { it?.claims?.get("admin")?.toString() == "true" }

  fun handleSignOut() {
    viewModelScope.launch {
      authProvider.auth().signOut()
      log.d(TAG, "creating anonymous signin on ${Thread.currentThread().name}")
      authProvider.auth().signInAnonymously().await()
    }
  }

  fun handleSignIn(credentialData: Bundle) {
    try {
            // Use googleIdTokenCredential and extract id to validate and
            // authenticate on your server.
            val googleIdTokenCredential = GoogleIdTokenCredential
              .createFrom(credentialData)
            log.d(TAG, "firebaseAuthWithGoogle:" + googleIdTokenCredential.id)
            val googleCredential =
              GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            firebaseAuthWithGoogle(googleCredential)
          } catch (e: GoogleIdTokenParsingException) {
            log.e(TAG, "Received an invalid google id token response", e)
          }
  }

  private fun firebaseAuthWithGoogle(credential: AuthCredential) {
    viewModelScope.launch {
      val currentUser = authProvider.auth().currentUser
      if (currentUser == null) firebaseSignInWithGoogle(credential)
      else {
        try {
          currentUser.linkWithCredential(credential).await()
          log.d(TAG, "linkWithCredential:success")
        } catch (e: Throwable) {
          log.e(TAG, "linkWithCredential:failure", e)
          firebaseSignInWithGoogle(credential, currentUser)
        }
      }
    }
  }

  private suspend fun firebaseSignInWithGoogle(
    credential: AuthCredential,
    oldUser: FirebaseUser? = null
  ) {
    try {
      authProvider.auth().signInWithCredential(credential).await()
      log.d(TAG, "signInWithCredential:success")
      if (oldUser?.isAnonymous == true) {
        oldUser.delete().await()
        log.d(TAG, "delete:success")
      }
    } catch (e: Throwable) {
      // If sign in fails, display a message to the user.
      log.w(TAG, "signInWithCredential:failure", e)
      userError = "Authentication Failed."
    }
  }

  fun shouldShowChangelog(): Flow<Boolean> {
    return savedStateRepo.getState(CHANGELOG_STATE)
      .mapLatest { it != null && it.value != versionCode.toString() }
  }

  fun setChangelogShown() {
    viewModelScope.launch {
      savedStateRepo.setState(CHANGELOG_STATE, versionCode.toString())
    }
  }

  var userError: String? by mutableStateOf(null)
    private set

  val featureFlags: Flow<ConfigProvider.Companion.RemoteConfig> by lazy {
    configProvider.currentConfig
  }

  companion object {
    private const val TAG = "UserViewModel"
    private const val CHANGELOG_STATE = "ChangelogState"
  }
}