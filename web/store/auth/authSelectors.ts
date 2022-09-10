import { createSelector } from 'reselect'

import { ReduxState } from '..'

export const getFirebaseToken = (state: ReduxState) => state.auth.firebaseToken
const getFirebaseUser = (state: ReduxState) => state.auth.firebaseUser
export const getFirebaseApp = (state: ReduxState) => state.auth.firebaseApp

export const getIsInitializing = createSelector(
  [getFirebaseApp],
  firebaseApp => firebaseApp === undefined
)

export const getIsLoggedIn = createSelector(
  [getFirebaseUser],
  firebaseUser => firebaseUser !== undefined
)

interface UserInfo {
  name?: string
  email?: string
}

export const getUserInfo = createSelector(
  [getFirebaseUser],
  firebaseUser =>
    ({
      name: firebaseUser?.displayName,
      email: firebaseUser?.email,
    } as UserInfo)
)

export const getFirebaseUserClaims = (state: ReduxState) => state.auth.claims
