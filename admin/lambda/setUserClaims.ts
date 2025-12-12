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

/**
 * Sets a custom claim on a Firebase Auth user's ID token.
 *
 * This is Step 1 of 3 for granting workout access:
 * 1. setUserClaims - Set the "group" claim on the user's token (e.g., "Group1")
 * 2. updateRefittedDynamoGroup - Map the group to workout code names in DynamoDB (helper for client)
 * 3. updateRefittedIamGroup - Update IAM policy to enforce DynamoDB access control
 *
 * The Android client reads the "group" claim from the ID token to determine which
 * DynamoDB group definition to query for the list of available workouts.
 *
 * @param email - User's email address
 * @param claimName - Custom claim name (typically "group")
 * @param claimValue - Claim value (e.g., "Group1" for paid tier access)
 */
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
