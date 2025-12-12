'use server'

import type { UserRecord } from 'firebase-admin/auth'
import { getAuthenticatedAuth, serverLogout } from './auth'

export async function listAllUsers(): Promise<
  { users: UserRecord[] } | undefined
> {
  try {
    const auth = await getAuthenticatedAuth()

    // Perform the authenticated operation
    const users = await auth.listUsers(1000)

    return users
  } catch (error) {
    console.error('Error listing users:', error)
    await serverLogout()
  }
}
