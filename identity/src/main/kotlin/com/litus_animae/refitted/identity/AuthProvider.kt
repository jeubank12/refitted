package com.litus_animae.refitted.identity

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.litus_animae.refitted.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthProvider @Inject constructor(private val log: LogUtil) {
  companion object {
    private const val TAG = "AuthProvider"
  }

  private val _deletedAccountEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  /** Emits when a signed-in (non-anonymous) account was found to be deleted server-side. */
  val deletedAccountEvents: SharedFlow<Unit> = _deletedAccountEvents.asSharedFlow()

  private val _auth by lazy {
    log.d(TAG, "initializing FirebaseAuth on ${Thread.currentThread().name}")
    val instance = FirebaseAuth.getInstance()

    log.d(TAG, "checking current user on ${Thread.currentThread().name}")
    val user = instance.currentUser
    if (user == null) {
      log.d(TAG, "creating anonymous signin on ${Thread.currentThread().name}")
      instance to instance.signInAnonymously().onSuccessTask {
        val completionSource = TaskCompletionSource<FirebaseUser>()
        completionSource.setResult(it.user)
        completionSource.task
      }
    } else {
      log.d(TAG, "returning cached user")
      val completionSource = TaskCompletionSource<FirebaseUser>()
      completionSource.setResult(user)
      instance to completionSource.task
    }
  }

  suspend fun auth(): FirebaseAuth {
    val (instance, userTask) = _auth
    userTask.await()
    return instance
  }

  suspend fun getIdToken(forceRefresh: Boolean = false): GetTokenResult? {
    val instance = auth()
    val user = instance.currentUser ?: return null
    return try {
      user.getIdToken(forceRefresh).await()
    } catch (e: FirebaseAuthInvalidUserException) {
      log.w(TAG, "current user no longer exists, signing in a new anonymous user", e)
      if (!user.isAnonymous) {
        _deletedAccountEvents.tryEmit(Unit)
      }
      instance.signOut()
      instance.signInAnonymously().await()
      instance.currentUser?.getIdToken(forceRefresh)?.await()
    }
  }

  val currentUser = callbackFlow {
    val (instance, userTask) = _auth

    log.d(TAG, "launching user flow on ${Thread.currentThread().name}")
    val listener = FirebaseAuth.AuthStateListener {
      trySend(it.currentUser)
    }
    instance.addAuthStateListener(listener)

    send(userTask.await())

    awaitClose {
      log.d(TAG, "closing user flow on ${Thread.currentThread().name}")
      instance.removeAuthStateListener(listener)
    }
  }.flowOn(Dispatchers.IO)
}