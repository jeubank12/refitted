package com.litus_animae.refitted.data.firebase

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.litus_animae.refitted.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthProvider @Inject constructor(log: LogUtil) {
  companion object {
    private const val TAG = "AuthProvider"
  }

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