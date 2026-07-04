'use server'

import { getAuthenticatedAuth, serverLogout } from './auth'

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
