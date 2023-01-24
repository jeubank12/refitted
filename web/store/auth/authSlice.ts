import { createSlice, PayloadAction } from '@reduxjs/toolkit'

import { getAnalytics } from 'firebase/analytics'
import { FirebaseApp, initializeApp } from 'firebase/app'
import { AppCheck, initializeAppCheck, getToken } from 'firebase/app-check'
import {
  browserSessionPersistence,
  getAuth,
  GoogleAuthProvider,
  setPersistence,
  signInWithPopup,
  User,
} from 'firebase/auth'

import { getFirebaseApp, getFirebaseAppCheck } from './authSelectors'
import { firebaseConfig, recaptchaProvider } from './firebase'
import { ReduxThunk } from 'store'

export interface AuthState {
  firebaseApp?: FirebaseApp
  firebaseAppCheck?: AppCheck
  firebaseUser?: User
  firebaseToken?: string
  error: string | null
  appCheckToken?: string
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

    logout: state => ({ ...initialState, firebaseApp: state.firebaseApp }),

    loginFailed: (state, action: PayloadAction<string>) => {
      state.error = action.payload
    },

    appCheckInitialized: (state, action: PayloadAction<AppCheck>) => {
      state.firebaseAppCheck = action.payload
    },

    appCheckToken: (state, action: PayloadAction<string>) => {
      state.appCheckToken = action.payload
    },
  },
})

const {
  appInitialized,
  appCheckInitialized,
  appCheckToken,
  login,
  loginFailed,
  logout,
} = authSlice.actions

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
      process.env.NEXT_PUBLIC_DEV_TOOLS_ENABLED === 'true'
    const appCheck = initializeAppCheck(newApp, {
      provider: recaptchaProvider,
    })
    dispatch(appCheckInitialized(appCheck))
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
        if (success.claims?.admin) {
          dispatch(
            login({
              user: userForToken,
              idToken: success.token,
            })
          )
          dispatch(getAppCheckToken)
        } else {
          dispatch(loginFailed('Insufficient Permissions'))
          dispatch(doLogout)
        }
      })
    }
  }

const getAppCheckToken: ReduxThunk = (dispatch, getState) => {
  const appCheck = getFirebaseAppCheck(getState())
  if (appCheck) {
    getToken(appCheck, /* forceRefresh */ false).then(success => {
      if (success) dispatch(appCheckToken(success.token))
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
