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
import { login, logout } from 'app/admin/actions/auth'

const provider = new GoogleAuthProvider()

export const useLogin = () => {
  const [error, setError] = useState<string | null>(null)

  const doLogin = useCallback(() => {
    const auth = getAuth(app)
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

export const useFirebaseUser = () => {
  const [firebaseUser, setFirebaseUser] = useState<User | null>()

  useEffect(() => {
    const auth = getAuth(app)
    return auth.onAuthStateChanged(user => {
      setFirebaseUser(user)
    })
  }, [])

  return firebaseUser
}

export const useFirebaseAuth = () => {
  useEffect(() => {
    const auth = getAuth(app)
    setPersistence(auth, browserSessionPersistence)
  }, [])
}

export const useFirebaseToken = () => {
  const [firebaseToken, setFirebaseToken] = useState<string | null>()

  useEffect(() => {
    const auth = getAuth(app)
    return auth.onIdTokenChanged(user => {
      console.debug('token change', user)
      if (user) {
        user.getIdToken().then(setFirebaseToken)
      }
    })
  }, [])

  return firebaseToken
}

export const useLogout = () => {
  const doLogout = useCallback(async () => {
    console.log('Logging out')
    const auth = getAuth(app)
    await logout()
    await auth.signOut()
  }, [])

  return { doLogout }
}
