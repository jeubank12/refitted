package com.litus_animae.refitted.models

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.litus_animae.refitted.BuildConfig
import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
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
  private val _isUserLoggedIn by lazy { MutableStateFlow(auth.currentUser != null) }
  val isUserLoggedIn by lazy { _isUserLoggedIn.asStateFlow() }

  fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
    try {
      val account = completedTask.getResult(ApiException::class.java)!!
      log.v(TAG, "Google user signed in, signing in firebase")
      firebaseAuthWithGoogle(account)
    } catch (e: ApiException) {
      // The ApiException status code indicates the detailed failure reason.
      // Please refer to the GoogleSignInStatusCodes class reference for more information.
      Log.w(TAG, "signInResult:failed code=" + e.statusCode, e)
    } catch (e: Exception) {
      Log.wtf(TAG, "Fatal Error", e)
    }
  }

  private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
    Log.d(TAG, "firebaseAuthWithGoogle:" + acct.id)
    val credential = GoogleAuthProvider.getCredential(acct.idToken, null)

    viewModelScope.launch {
      try {
        auth.signInWithCredential(credential).await()
        if (auth.currentUser != null) {
          // Sign in success, update UI with the signed-in user's information
          log.d(TAG, "signInWithCredential:success")
          _isUserLoggedIn.emit(true)
        } else {
          log.w(TAG, "signInWithCredential:incomplete")
        }
      } catch (e: Throwable) {
        // If sign in fails, display a message to the user.
        log.w(TAG, "signInWithCredential:failure", e)
        //Snackbar.make(findViewById(R.id.main_layout), "Authentication Failed.", Snackbar.LENGTH_SHORT).show();
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

  companion object {
    private const val TAG = "UserViewModel"
    private const val ChangelogState = "ChangelogState"
  }
}