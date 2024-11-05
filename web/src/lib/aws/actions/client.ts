import { cookies } from 'next/headers'

import { fromCognitoIdentityPool } from '@aws-sdk/credential-providers'
import { LambdaClient } from '@aws-sdk/client-lambda'

import { clientConfig, identityPoolId } from '../awsConfig'
import { firebaseLogins } from 'src/lib/firebase/firebaseConfig'

export function getClient() {
  const cookie = cookies().get('session')
  const session = JSON.parse(cookie?.value ?? '')
  if (!session.idToken) return
  const token = session.idToken
  const credentials = fromCognitoIdentityPool({
    identityPoolId,
    logins: firebaseLogins(token),
    clientConfig,
  })
  return new LambdaClient({ region: 'us-east-2', credentials })
}
