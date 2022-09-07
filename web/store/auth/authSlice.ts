import { createSlice, PayloadAction } from '@reduxjs/toolkit'

import { getAnalytics } from 'firebase/analytics'
import { FirebaseApp, initializeApp } from 'firebase/app'
import { initializeAppCheck } from 'firebase/app-check'
import {
  browserSessionPersistence,
  getAuth,
  GoogleAuthProvider,
  ParsedToken,
  setPersistence,
  signInWithPopup,
  User,
} from 'firebase/auth'

import { getFirebaseApp } from './authSelectors'
import { firebaseConfig, recaptchaProvider } from './firebase'
import { ReduxThunk } from 'store'

export interface AuthState {
  firebaseApp?: FirebaseApp
  firebaseUser?: User
  firebaseToken?: string
  claims?: ParsedToken
  error: string | null
}

const initialState: AuthState = {
  error: null,
}

interface Login {
  user: User
  idToken: string
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    appInitialized: (state, action: PayloadAction<FirebaseApp>) => {
      state.firebaseApp = action.payload
    },

    login: (state, action: PayloadAction<Login>) => {
      const { user, idToken } = action.payload
      state.firebaseUser = user
      state.firebaseToken = idToken
      state.error = null
    },

    setClaims: (state, action: PayloadAction<ParsedToken>) => {
      state.claims = action.payload
    },

    logout: state => ({ ...initialState, firebaseApp: state.firebaseApp }),

    loginFailed: (state, action: PayloadAction<string>) => {
      state.error = action.payload
    },
  },
})

const { appInitialized, login, loginFailed, setClaims, logout } =
  authSlice.actions

const provider = new GoogleAuthProvider()

export const doLogin: ReduxThunk = (dispatch, getState) => {
  const app = getFirebaseApp(getState())
  if (!app) {
    dispatch(loginFailed('Firebase not initialized'))
  } else {
    const auth = getAuth(app)
    signInWithPopup(auth, provider).then(
      success => {
        const credential = GoogleAuthProvider.credentialFromResult(success)
        if (!credential?.idToken) dispatch(loginFailed('Failed to get idToken'))
        dispatch(finishLogin(app))
      },
      error => dispatch(loginFailed(error.message))
    )
  }
}

export const initializeFirebase: ReduxThunk = (dispatch, getState) => {
  const app = getFirebaseApp(getState())
  if (!app) {
    const newApp = initializeApp(firebaseConfig)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/no-extra-semi
    ;(window as any).FIREBASE_APPCHECK_DEBUG_TOKEN =
      !!process.env.NEXT_PUBLIC_DEV_TOOLS_ENABLED
    initializeAppCheck(newApp, {
      provider: recaptchaProvider,
    })
    getAnalytics(newApp)
    const auth = getAuth(newApp)
    setPersistence(auth, browserSessionPersistence).then(() => {
      dispatch(appInitialized(newApp))
      dispatch(finishLogin(newApp))
    })
  }
}

const finishLogin: (app: FirebaseApp) => ReduxThunk =
  (app: FirebaseApp) => dispatch => {
    const auth = getAuth(app)
    const userForToken = auth.currentUser
    if (userForToken) {
      userForToken.getIdTokenResult().then(success => {
        dispatch(
          login({
            user: userForToken,
            idToken: success.token,
          })
        )
        dispatch(setClaims(success.claims))
      })
    }
  }

export const doLogout: ReduxThunk = (dispatch, getState) => {
  const app = getFirebaseApp(getState())
  if (app) {
    const auth = getAuth(app)
    auth.signOut()
    dispatch(logout())
  }
}

export default authSlice.reducer
