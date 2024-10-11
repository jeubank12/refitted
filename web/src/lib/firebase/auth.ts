import { createContext, useCallback, useContext, useState } from 'react'

import { AppCheck, getToken } from 'firebase/app-check'
import {
  browserSessionPersistence,
  getAuth,
  GoogleAuthProvider,
  setPersistence,
  signInWithPopup,
  User,
} from 'firebase/auth'

import { app, useAppCheck } from './firebaseApp'

interface UserState {
  initialized: boolean
  firebaseUser?: User
  setFirebaseUser: (user: User | undefined) => void
  firebaseToken?: string
  setFirebaseToken: (token: string | undefined) => void
}
export const UserContext = createContext<UserState>({
  initialized: false,
  setFirebaseUser: () => {},
  setFirebaseToken: () => {},
})

const provider = new GoogleAuthProvider()

export const useLogin = () => {
  const { setFirebaseUser, setFirebaseToken } = useContext(UserContext)
  const [error, setError] = useState<string | null>(null)
  const appCheck = useAppCheck()

  const doLogin = useCallback(() => {
    const auth = getAuth(app)
    signInWithPopup(auth, provider).then(
      success => {
        const credential = GoogleAuthProvider.credentialFromResult(success)
        if (!credential?.idToken) setError('Failed to get idToken')
        finishLogin(setFirebaseUser, setFirebaseToken, appCheck, setError)
      },
      error => setError(error.message)
    )
  }, [])
  return { error, doLogin }
}

export const useUserContext = (): UserState => {
  const [initialized, setInitialized] = useState(false)
  const [firebaseUser, setFirebaseUser] = useState<User>()
  const [firebaseToken, setFirebaseToken] = useState<string>()

  const appCheck = useAppCheck()

  const auth = getAuth(app)
  setPersistence(auth, browserSessionPersistence).then(async () => {
    await finishLogin(setFirebaseUser, setFirebaseToken, appCheck, null)
    setInitialized(true)
  })
  return {
    initialized,
    firebaseUser,
    setFirebaseUser,
    firebaseToken,
    setFirebaseToken,
  }
}

const finishLogin = (
  setFirebaseUser: (user: User | undefined) => void,
  setFirebaseToken: (token: string | undefined) => void,
  appCheck: AppCheck | null,
  setError: ((error: string) => void) | null
) => {
  const auth = getAuth(app)
  const userForToken = auth.currentUser
  if (userForToken) {
    return userForToken.getIdTokenResult().then(async success => {
      if (success.claims?.admin) {
        setFirebaseUser(userForToken), setFirebaseToken(success.token)
        if (!appCheck) throw new Error('app check not initialized')
        await getToken(appCheck, /* forceRefresh */ false)
      } else {
        setError?.('Insufficient Permissions')
        auth.signOut()
        setFirebaseUser(undefined)
        setFirebaseToken(undefined)
      }
    })
  }
}

// const getAppCheckToken: ReduxThunk = (dispatch, getState) => {
//   getToken(appCheck, /* forceRefresh */ false).then(success => {
//     if (success) dispatch(appCheckToken(success.token))
//   })
// }

export const useLogout = () => {
  const { setFirebaseUser, setFirebaseToken } = useContext(UserContext)

  const doLogout = useCallback(() => {
    const auth = getAuth(app)
    auth.signOut()
    setFirebaseUser(undefined)
    setFirebaseToken(undefined)
  }, [setFirebaseUser, setFirebaseToken])

  return { doLogout }
}
