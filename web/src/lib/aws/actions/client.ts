'use server'

import { fromCognitoIdentityPool } from '@aws-sdk/credential-providers'
import { LambdaClient } from '@aws-sdk/client-lambda'

import { clientConfig, identityPoolId } from '../awsConfig'
import { firebaseLogins } from 'src/lib/firebase/firebaseConfig'
import { getIdToken } from 'src/lib/firebase/actions/auth'

export async function getClient() {
  const idToken = await getIdToken()
  if (!idToken) return
  const credentials = fromCognitoIdentityPool({
    identityPoolId,
    logins: firebaseLogins(idToken),
    clientConfig,
  })
  return new LambdaClient({ region: 'us-east-2', credentials })
}
