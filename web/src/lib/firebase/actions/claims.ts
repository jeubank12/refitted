'use server'

import { adminAuth } from '../admin'
import { getAuthenticatedAuth, serverLogout, validateAppCheck } from './auth'
import { SetClaimResult } from 'src/lib/aws/types'

// Claims an admin is allowed to set from this action. Deliberately excludes
// sensitive claims like `admin`: the server action is a directly-invocable
// endpoint, so it must constrain the claim name itself rather than trusting the
// UI (which only ever sends `group`). Admin promotion is intentionally manual
// for now — add `admin` here when that flow exists.
const SETTABLE_CLAIMS = new Set(['group'])

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

  if (!SETTABLE_CLAIMS.has(claimName)) {
    console.warn('setUserClaim refused: claim not settable', claimName)
    return { ok: false, error: `Claim "${claimName}" cannot be set here` }
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
    const claims = { ...user.customClaims }
    if (claimValue) {
      claims[claimName] = claimValue
    } else {
      delete claims[claimName]
    }
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
