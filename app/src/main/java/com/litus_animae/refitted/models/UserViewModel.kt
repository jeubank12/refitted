package com.litus_animae.refitted.models

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
import com.litus_animae.refitted.BuildConfig
import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.data.firebase.AuthProvider
import com.litus_animae.refitted.data.firebase.ConfigProvider
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@ExperimentalCoroutinesApi
@HiltViewModel
class UserViewModel @Inject constructor(
  private val log: LogUtil,
  private val savedStateRepo: SavedStateRepository,
  private val authProvider: AuthProvider,
  private val configProvider: ConfigProvider
) : ViewModel() {

  val userEmail =
    authProvider.currentUser
      .map { it?.email }
      .map {
        if (it.isNullOrBlank()) {
          null
        } else it
      }

  val userIsAdmin =
    authProvider.currentUser
      .map { it?.getIdToken(false)?.await() }
      .map { it?.claims?.get("admin")?.toString() == "true" }

  fun handleSignOut() {
    viewModelScope.launch {
      // TODO need to re-auth as anonymous user
      authProvider.auth().signOut()
    }
  }

  fun handleSignIn(result: GetCredentialResponse) {
    // Handle the successfully returned credential.
    when (val credential = result.credential) {

      // GoogleIdToken credential
      is CustomCredential -> {
        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          try {
            // Use googleIdTokenCredential and extract id to validate and
            // authenticate on your server.
            val googleIdTokenCredential = GoogleIdTokenCredential
              .createFrom(credential.data)
            Log.d(TAG, "firebaseAuthWithGoogle:" + googleIdTokenCredential.id)
            val googleCredential =
              GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            firebaseAuthWithGoogle(googleCredential)
          } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Received an invalid google id token response", e)
          }
        } else {
          // Catch any unrecognized custom credential type here.
          Log.e(TAG, "Unexpected type of credential")
        }
      }

      else -> {
        // Catch any unrecognized credential type here.
        Log.e(TAG, "Unexpected type of credential")
      }
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
    return savedStateRepo.getState(ChangelogState)
      .mapLatest { it != null && it.value != BuildConfig.VERSION_CODE.toString() }
  }

  fun setChangelogShown() {
    viewModelScope.launch {
      savedStateRepo.setState(ChangelogState, BuildConfig.VERSION_CODE.toString())
    }
  }

  var userError: String? by mutableStateOf(null)
    private set

  val featureFlags by lazy {
    configProvider.currentConfig
  }

  companion object {
    private const val TAG = "UserViewModel"
    private const val ChangelogState = "ChangelogState"
  }
}