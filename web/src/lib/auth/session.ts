'use server'

import { cookies } from 'next/headers'
import { verifySessionJwt, isJwt, type SessionPayload } from './jwt'

/**
 * Session Extraction Utilities
 *
 * This module provides utilities for extracting session data from signed JWTs.
 * Handles both JWT and legacy plain JSON sessions for backward compatibility.
 *
 * ## Migration Strategy
 *
 * During migration from plain JSON to JWT sessions:
 * - JWT sessions: Verify signature and return payload
 * - Legacy plain JSON sessions: Delete cookie and return null (forces re-login)
 *
 * Users with legacy sessions will seamlessly re-login once and get JWT sessions.
 *
 * ## Usage
 *
 * ```typescript
 * const session = await getSession()
 * if (session) {
 *   console.log(session.email, session.isAdmin)
 * }
 * ```
 */

/**
 * Legacy session structure (plain JSON, before JWT migration)
 */
interface LegacySession {
  isAdmin?: boolean
  idToken?: string
  appCheckToken?: string
}

/**
 * Gets the session payload from cookie
 *
 * Handles both JWT and legacy plain JSON sessions for backward compatibility.
 *
 * **Migration Handling:**
 * - JWT format: Verifies signature and decodes payload
 * - Plain JSON format: Logs warning, deletes cookie, returns null
 * - Invalid/expired: Returns null
 *
 * **Security:**
 * - JWT signature is verified before returning payload
 * - Legacy sessions are rejected (forces re-login)
 * - Malformed cookies return null (no exceptions)
 *
 * @returns Session payload if valid JWT, null if invalid/missing/legacy
 */
export async function getSession(): Promise<SessionPayload | null> {
  const cookie = (await cookies()).get('session')
  if (!cookie?.value) return null

  // Check if JWT or legacy plain JSON
  if (isJwt(cookie.value)) {
    // Verify JWT signature and decode
    return await verifySessionJwt(cookie.value)
  } else {
    // Handle legacy plain JSON session
    try {
      const legacy: LegacySession = JSON.parse(cookie.value)

      // Legacy sessions don't have userId/email, return null to force re-login
      // This ensures migration to JWT sessions
      console.warn(
        'Legacy plain JSON session detected, forcing re-login to migrate to JWT'
      )

      // Delete legacy session
      const requestCookies = await cookies()
      requestCookies.delete('session')

      return null
    } catch (error) {
      console.error('Failed to parse session cookie:', error)
      return null
    }
  }
}

/**
 * Gets ID token from session
 *
 * Extracts the Firebase ID token from the signed JWT session.
 * This token is used for Firebase Admin SDK operations.
 *
 * @returns Firebase ID token or undefined if no session
 */
export async function getIdToken(): Promise<string | undefined> {
  const session = await getSession()
  return session?.idToken
}

/**
 * Gets App Check token from session
 *
 * Extracts the Firebase App Check token from the signed JWT session.
 * This token is used for Firebase Admin SDK app integrity verification.
 *
 * @returns Firebase App Check token or undefined if no session
 */
export async function getAppCheckToken(): Promise<string | undefined> {
  const session = await getSession()
  return session?.appCheckToken
}

/**
 * Gets user info from session (without Firebase call)
 *
 * Extracts user information from the signed JWT session.
 * This is much faster than calling Firebase Admin SDK to get user info.
 *
 * **Performance:**
 * - Before: Required Firebase Admin SDK call to get user info
 * - After: Extracts from JWT payload (no network call)
 *
 * @returns User info object or undefined if no session
 */
export async function getUserInfo(): Promise<
  | {
      userId: string
      email: string
      isAdmin: boolean
    }
  | undefined
> {
  const session = await getSession()
  if (!session) return undefined

  return {
    userId: session.userId,
    email: session.email,
    isAdmin: session.isAdmin,
  }
}

/**
 * Checks if current session has admin privileges
 *
 * Trusts the isAdmin flag from the signed JWT (signature already verified).
 *
 * **Security:**
 * - JWT signature ensures isAdmin hasn't been tampered with
 * - No need to validate Firebase tokens to check admin status
 * - Much faster than calling Firebase Admin SDK
 *
 * @returns True if user has admin privileges, false otherwise
 */
export async function isAdmin(): Promise<boolean> {
  const session = await getSession()
  return session?.isAdmin ?? false
}
