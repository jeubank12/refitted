'use server'

import {
  getAuthenticatedAuth,
  type AuthenticationError,
} from './validateTokens'

import type { UserRecord } from 'firebase-admin/auth'

/**
 * Lists all users in the Firebase Auth system
 *
 * This function demonstrates the standard pattern for authenticated server actions:
 * 1. Get authenticated Auth instance (validates tokens + initializes Firebase Admin)
 * 2. Return error if authentication fails
 * 3. Perform authenticated operation
 *
 * **Error Handling:**
 * - Returns AuthenticationError on validation failure
 * - Client components should check for error and trigger logout
 * - Do NOT redirect from server actions (causes logout bugs)
 *
 * @returns List of users or AuthenticationError
 */
export async function listAllUsers(): Promise<
  { users: UserRecord[] } | AuthenticationError
> {
  // Get authenticated Auth instance (handles validation + initialization)
  const authOrError = await getAuthenticatedAuth()
  if ('error' in authOrError) {
    return authOrError
  }

  // Perform the authenticated operation
  const users = await authOrError.listUsers(1000)

  return users
}
