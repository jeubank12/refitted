import { fromCognitoIdentityPool } from '@aws-sdk/credential-providers'
import { createSelector } from 'reselect'
import { ReduxState } from '../store'
import { clientConfig, identityPoolId } from './aws'
import { firebaseLogins } from './firebase'

const getFirebaseToken = (state: ReduxState) => state.auth.firebaseToken
const getFirebaseUser = (state: ReduxState) => state.auth.firebaseUser
export const getFirebaseApp = (state: ReduxState) => state.auth.firebaseApp

export const getAwsCredentials = createSelector(
  [getFirebaseToken],
  firebaseToken =>
    firebaseToken &&
    fromCognitoIdentityPool({
      identityPoolId,
      logins: firebaseLogins(firebaseToken),
      clientConfig,
    })
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
