package com.litus_animae.refitted.models

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.litus_animae.refitted.BuildConfig
import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@ExperimentalCoroutinesApi
@HiltViewModel
class UserViewModel @Inject constructor(
  private val log: LogUtil,
  private val savedStateRepo: SavedStateRepository
) : ViewModel() {

  private val auth by lazy { FirebaseAuth.getInstance() }
  private val _currentUser by lazy { MutableStateFlow(auth.currentUser) }
  val userEmail by lazy {
    _currentUser.asStateFlow().map { it?.email }
      .stateIn(viewModelScope, SharingStarted.Lazily, initialValue = auth.currentUser?.email)
  }

  fun handleSignOut() {
    auth.signOut()
    viewModelScope.launch { _currentUser.emit(null) }
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
      try {
        auth.signInWithCredential(credential).await()
        if (auth.currentUser != null) {
          // Sign in success, update UI with the signed-in user's information
          log.d(TAG, "signInWithCredential:success")
          _currentUser.emit(auth.currentUser)
        } else {
          log.w(TAG, "signInWithCredential:incomplete")
        }
      } catch (e: Throwable) {
        // If sign in fails, display a message to the user.
        log.w(TAG, "signInWithCredential:failure", e)
        userError = "Authentication Failed."
      }
    }
  }

  fun shouldShowChangelog(): Flow<Boolean> {
    return savedStateRepo.getState(ChangelogState)
      .mapLatest { it == null || it.value != BuildConfig.VERSION_CODE.toString() }
  }

  fun setChangelogShown() {
    viewModelScope.launch {
      savedStateRepo.setState(ChangelogState, BuildConfig.VERSION_CODE.toString())
    }
  }

  var userError: String? by mutableStateOf(null)
    private set

  companion object {
    private const val TAG = "UserViewModel"
    private const val ChangelogState = "ChangelogState"
  }
}