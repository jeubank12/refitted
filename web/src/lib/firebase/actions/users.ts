'use server'

import { cookies } from 'next/headers'

import { adminAuth } from '../admin'
import {
  getAuthenticatedAuth,
  serverLogout,
  validateAppCheck,
} from './auth'
import { DeleteUsersResult } from 'src/lib/aws/types'

import type { UserRecord } from 'firebase-admin/auth'

export async function listAllUsers(): Promise<
  { users: UserRecord[] } | undefined
> {
  try {
    const auth = await getAuthenticatedAuth()

    // Perform the authenticated operation
    const users = await auth.listUsers(1000)

    return users
  } catch (error) {
    const code = (error as { code?: unknown })?.code
    if (typeof code === 'string' && code.startsWith('auth/')) {
      // Expected: the session ended (logout, expiry) between render and this
      // call. Not worth an error-level log — clean up and let the redirect happen.
      console.debug('Session invalid while listing users, logging out', code)
      await serverLogout()
    } else {
      console.error('Error listing users:', error)
    }
    throw error
  }
}

export async function deleteUsers(
  uids: string[],
  appCheckToken: string
): Promise<DeleteUsersResult> {
  const validationError = await validateAppCheck(appCheckToken)
  if (validationError) {
    console.error('deleteUsers refused', validationError)
    return { ok: false, error: 'app-check-failed' }
  }

  try {
    await getAuthenticatedAuth()
  } catch (error) {
    console.error('Session invalid during deleteUsers', error)
    await serverLogout()
    return { ok: false, error: 'not-authorized' }
  }

  // Never let an admin delete their own account from this table.
  // Fail closed: if we can't positively resolve the caller's own uid, we can't
  // guarantee they aren't in the delete set, so refuse rather than risk letting
  // an admin delete their own account.
  const cookieStore = await cookies()
  const session = cookieStore.get('session')?.value
  let selfUid: string
  try {
    if (!session) throw new Error('No session cookie')
    selfUid = (await adminAuth().verifySessionCookie(session, true)).sub
  } catch (error) {
    console.error('Failed to resolve current admin uid during deleteUsers', error)
    return { ok: false, error: 'not-authorized' }
  }
  const targets = uids.filter(uid => uid !== selfUid)
  if (!targets.length) {
    return { ok: false, error: 'No deletable users in selection' }
  }

  try {
    const result = await adminAuth().deleteUsers(targets)
    return {
      ok: true,
      successCount: result.successCount,
      failureCount: result.failureCount,
      errors: result.errors.map(e => e.error.message),
    }
  } catch (error) {
    console.error('Error deleting users:', error)
    return { ok: false, error: 'Failed to delete users' }
  }
}
