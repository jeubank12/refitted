package com.litus_animae.refitted.data.firebase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class AuthProvider {
  val auth by lazy { FirebaseAuth.getInstance() }

  val currentUser = callbackFlow {
    send(auth.currentUser)
    val listener = FirebaseAuth.AuthStateListener {
      trySend(it.currentUser)
    }
    auth.addAuthStateListener(listener)
    awaitClose {
      auth.removeAuthStateListener(listener)
    }
  }
}