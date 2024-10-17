import { useCallback, useEffect, useState } from 'react'

import { getToken } from 'firebase/app-check'
import {
  browserSessionPersistence,
  getAuth,
  GoogleAuthProvider,
  setPersistence,
  signInWithPopup,
  User,
} from 'firebase/auth'

import { app, useAppCheck } from './firebaseApp'

const provider = new GoogleAuthProvider()

export const useLogin = () => {
  const [error, setError] = useState<string | null>(null)
  const { getAppCheckToken } = useAppCheckToken()

  const finishLogin = useFinishLogin(getAppCheckToken, setError)

  const doLogin = useCallback(() => {
    const auth = getAuth(app)
    signInWithPopup(auth, provider).then(
      async success => {
        const credential = GoogleAuthProvider.credentialFromResult(success)
        if (!credential?.idToken) setError('Failed to get idToken')
        await finishLogin()
      },
      error => setError(error.message)
    )
  }, [])
  return { error, doLogin }
}

export const useFirebaseAuth = (
  setFirebaseUser: (user: User | null) => void
) => {
  const { getAppCheckToken } = useAppCheckToken()

  useEffect(() => {
    const auth = getAuth(app)
    return auth.onAuthStateChanged(user => {
      console.debug('auth user', user)
      setFirebaseUser(user)
    })
  }, [])

  const finishLogin = useFinishLogin(getAppCheckToken, null)

  useEffect(() => {
    const auth = getAuth(app)
    setPersistence(auth, browserSessionPersistence).then(() => finishLogin())
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
  }, [setFirebaseToken])

  return firebaseToken
}

const useFinishLogin = (
  getAppCheckToken: () => Promise<string>,
  setError: ((error: string) => void) | null
) => {
  const finishLogin = useCallback(() => {
    const auth = getAuth(app)
    const userForToken = auth.currentUser
    if (userForToken) {
      return userForToken.getIdTokenResult().then(async success => {
        if (success.claims?.admin) {
          console.debug('logged in as', userForToken)
          await getAppCheckToken()
        } else {
          if (setError) setError('Insufficient Permissions')
          else console.error('Insufficient Permissions')
          auth.signOut()
        }
      })
    } else {
      console.log('no user logged in')
    }
  }, [getAppCheckToken])
  return finishLogin
}

export const useAppCheckToken = () => {
  const appCheck = useAppCheck()
  const getAppCheckToken = useCallback(() => {
    if (!appCheck.current) throw new Error('app check not initialized')
    return getToken(appCheck.current, /* forceRefresh */ false).then(
      success => success.token
    )
  }, [appCheck])

  return { getAppCheckToken }
}

export const useLogout = () => {
  const doLogout = useCallback(() => {
    console.log('Logging out')
    const auth = getAuth(app)
    auth.signOut()
  }, [])

  return { doLogout }
}
