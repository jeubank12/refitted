import { initializeApp, cert, ServiceAccount } from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'

import serviceAccount from '../firebase.json' assert { type: 'json' }

initializeApp({
  credential: cert(serviceAccount as ServiceAccount),
  databaseURL: 'https://refitted-361ee.firebaseio.com',
})

interface SetUserClaimsEvent {
  email?: string
  claimName?: string
  claimValue?: object
}

const setUserClaims = async ({
  email,
  claimName,
  claimValue,
}: SetUserClaimsEvent) => {
  if (!email) throw new Error('Email not set')
  if (!claimName) throw new Error('Claim Name not set')
  if (!claimValue) throw new Error('Claim Value not set')

  try {
    const user = await getAuth().getUserByEmail(email)
    // Confirm user is verified.
    if (user.emailVerified) {
      // Add custom claims for additional privileges.
      // This will be picked up by the user on token refresh or next sign in on new device.
      return await getAuth().setCustomUserClaims(user.uid, {
        ...user.customClaims,
        [claimName]: claimValue,
      })
    }
  } catch (error) {
    console.log(error)
  }
}

export const handler = setUserClaims
