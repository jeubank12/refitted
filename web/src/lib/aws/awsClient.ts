import { fromCognitoIdentityPool } from '@aws-sdk/credential-providers'
import { LambdaClient } from '@aws-sdk/client-lambda'

import { clientConfig, identityPoolId } from './awsConfig'
import { firebaseLogins } from 'src/lib/firebase/firebaseConfig'
import { useFirebaseToken } from '../firebase/auth'

export const useAwsCredentials = () => {
  const firebaseToken = useFirebaseToken()

  if (firebaseToken) {
    return fromCognitoIdentityPool({
      identityPoolId,
      logins: firebaseLogins(firebaseToken),
      clientConfig,
    })
  }
}

export const useLambdaClient = () => {
  const credentials = useAwsCredentials()
  if (credentials) return new LambdaClient({ region: 'us-east-2', credentials })
}
