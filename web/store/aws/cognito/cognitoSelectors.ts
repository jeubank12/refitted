import { createSelector } from 'reselect'

import { fromCognitoIdentityPool } from '@aws-sdk/credential-providers'

import { clientConfig, identityPoolId } from '../aws'
import { firebaseLogins } from 'store/auth/firebase'
import { getFirebaseToken } from 'store/auth/authSelectors'

export const getAwsCredentials = createSelector(
  [getFirebaseToken],
  firebaseToken => {
    if (firebaseToken)
      return fromCognitoIdentityPool({
        identityPoolId,
        logins: firebaseLogins(firebaseToken),
        clientConfig,
      })
  }
)