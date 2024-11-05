import { useCallback, useEffect, useState } from 'react'

import {
  browserSessionPersistence,
  getAuth,
  GoogleAuthProvider,
  setPersistence,
  signInWithPopup,
  User,
} from 'firebase/auth'

import { app } from './firebaseApp'
import { login, logout, refreshSession } from 'src/lib/firebase/actions/auth'

const provider = new GoogleAuthProvider()

export const useLogin = () => {
  const [error, setError] = useState<string | null>(null)

  const doLogin = useCallback(() => {
    const auth = getAuth(app)
    setPersistence(auth, browserSessionPersistence)
    signInWithPopup(auth, provider).then(
      async success => {
        const credential = GoogleAuthProvider.credentialFromResult(success)
        // TODO does this also need to sign out?
        if (!credential?.idToken) {
          setError('Failed to get idToken')
          return
        }
        await auth.authStateReady()
        if (!auth.currentUser) {
          setError('not logged in')
          return
        }
        const idToken = await auth.currentUser.getIdToken()
        login(idToken)
      },
      error => setError(error.message)
    )
  }, [])
  return { error, doLogin }
}

export const useUserSession = () => {
  const [firebaseUser, setFirebaseUser] = useState<User | null>()

  useEffect(() => {
    const auth = getAuth(app)
    return auth.onAuthStateChanged(user => {
      console.log('user change', user)
      setFirebaseUser(user)
    })
  }, [])

  useEffect(() => {
    const auth = getAuth(app)
    return auth.onIdTokenChanged(async user => {
      console.debug('token change', user)
      const idToken = await user?.getIdToken()
      await refreshSession(idToken)
    })
  }, [])

  const doLogout = useCallback(async () => {
    console.log('Logging out')
    const auth = getAuth(app)
    await logout()
    await auth.authStateReady()
    await auth.signOut()
  }, [])

  return { logout: doLogout, firebaseUser }
}
