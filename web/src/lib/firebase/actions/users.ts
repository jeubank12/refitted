'use server'

import type { UserRecord } from 'firebase-admin/auth'
import { getAuthenticatedAuth } from './auth'
import { redirect } from 'next/navigation'

export async function listAllUsers(): Promise<{ users: UserRecord[] }> {
  try {
    const auth = await getAuthenticatedAuth()

    // Perform the authenticated operation
    const users = await auth.listUsers(1000)

    return users
  } catch (error) {
    console.error('Error listing users:', error)
    return redirect('/admin')
  }
}
