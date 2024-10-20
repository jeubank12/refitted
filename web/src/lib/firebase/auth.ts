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

const provider = new GoogleAuthProvider()

export const useLogin = () => {
  const [error, setError] = useState<string | null>(null)

  const doLogin = useCallback(() => {
    const auth = getAuth(app)
    signInWithPopup(auth, provider).then(
      async success => {
        const credential = GoogleAuthProvider.credentialFromResult(success)
        if (!credential?.idToken) setError('Failed to get idToken')
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

export const useIsAdmin = (setError?: (error: string) => void) => {
  const firebaseUser = useFirebaseUser()
  const [isAdmin, setIsAdmin] = useState<boolean>()

  useEffect(() => {
    if (firebaseUser) {
      firebaseUser.getIdTokenResult().then(async success => {
        if (success.claims?.admin) {
          console.debug('logged in as', firebaseUser)
          setIsAdmin(true)
        } else {
          if (setError) setError('Insufficient Permissions')
          else console.error('Insufficient Permissions')
          // auth.signOut()
          // TODO replace with 401 page
          setIsAdmin(false)
        }
      })
    } else {
      setIsAdmin(undefined)
      console.log('no user logged in')
    }
  }, [])

  return isAdmin
}

export const useLogout = () => {
  const doLogout = useCallback(() => {
    console.log('Logging out')
    const auth = getAuth(app)
    return auth.signOut()
  }, [])

  return { doLogout }
}
