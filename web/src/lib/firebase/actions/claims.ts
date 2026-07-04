'use server'

import { adminAuth } from '../admin'
import { getAuthenticatedAuth, serverLogout, validateAppCheck } from './auth'
import { SetClaimResult } from 'src/lib/aws/types'

export async function setUserClaim(
  email: string,
  claimName: string,
  claimValue: string,
  appCheckToken: string
): Promise<SetClaimResult> {
  const validationError = await validateAppCheck(appCheckToken)
  if (validationError) {
    console.error('setUserClaim refused', validationError)
    return { ok: false, error: 'app-check-failed' }
  }

  try {
    await getAuthenticatedAuth()
  } catch (error) {
    console.error('Session invalid during setUserClaim', error)
    await serverLogout()
    return { ok: false, error: 'not-authorized' }
  }

  try {
    const user = await adminAuth().getUserByEmail(email)
    if (!user.emailVerified) {
      return { ok: false, error: 'User email is not verified' }
    }
    const claims = { ...user.customClaims, [claimName]: claimValue }
    await adminAuth().setCustomUserClaims(user.uid, claims)
    return { ok: true, claims }
  } catch (error) {
    const code = (error as { code?: unknown })?.code
    if (code === 'auth/user-not-found') {
      return { ok: false, error: 'No user found with that email' }
    }
    console.error('Error setting user claim', error)
    return { ok: false, error: 'Failed to set claim' }
  }
}
