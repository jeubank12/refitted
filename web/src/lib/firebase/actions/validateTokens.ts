/**
 * Token Validation Module
 *
 * This module provides centralized Firebase token validation for operations
 * that require Firebase Admin SDK access.
 *
 * ## IMPORTANT: When to Use These Functions
 *
 * ### ✅ Use validateTokens() or getAuthenticatedAuth() when:
 * - Calling Firebase Admin SDK (getAuth(), getAppCheck(), etc.)
 * - During login/refreshSession (initial token verification)
 * - Example: listAllUsers() needs Firebase Admin Auth
 *
 * ### ❌ Do NOT use validateTokens() when:
 * - In general server actions (JWT already verified by proxy middleware)
 * - Checking isAdmin status (trust signed JWT payload)
 * - Getting user info (use getUserInfo() from session.ts instead)
 *
 * ## Why This Architecture?
 *
 * **Before JWT Implementation:**
 * - Every server action validated Firebase tokens (~100-200ms per call)
 * - Heavy load on Firebase Admin SDK
 * - Expensive and slow
 *
 * **After JWT Implementation:**
 * - Proxy middleware verifies JWT signature (<1ms)
 * - Server actions trust signed JWT payload
 * - Only validate Firebase tokens when actually calling Firebase Admin SDK
 * - Much better performance and lower Firebase quota usage
 *
 * ## Authentication Architecture Overview
 *
 * This application uses a **hybrid two-layer security model**:
 *
 * ### Layer 1: Middleware (JWT Signature Verification)
 * - Runs in Next.js Edge runtime
 * - Verifies JWT signature with HMAC-SHA256
 * - Trusts isAdmin flag from signed payload
 * - Purpose: Fast routing decisions with cryptographic security
 *
 * ### Layer 2: Server Actions (Conditional Firebase Validation)
 * - Runs in Node.js runtime (full Firebase Admin SDK available)
 * - Only validates Firebase tokens when calling Firebase Admin SDK
 * - Uses getAuthenticatedAuth() for Firebase operations
 * - Purpose: Firebase-specific security enforcement
 *
 * ## Token Lifecycle
 *
 * 1. **Login**: User authenticates with Google OAuth
 *    - Client obtains idToken from Firebase Auth
 *    - Client obtains appCheckToken from Firebase App Check
 *    - Server creates session cookie with both tokens
 *
 * 2. **Token Refresh**: Client-side token refresh via Firebase hooks
 *    - onIdTokenChanged event triggers when token expires
 *    - Client calls refreshSession() server action
 *    - Server updates session cookie with new tokens
 *
 * 3. **Validation**: On each authenticated server action
 *    - Call validateTokens() at the start
 *    - Firebase Admin SDK verifies signatures and expiration
 *    - Action proceeds only if validation succeeds
 *
 * 4. **Expiration/Logout**: When tokens are invalid
 *    - Server action returns error object
 *    - Client detects error and triggers logout
 *    - Client clears Firebase client state
 *    - Server clears session cookie
 *
 * ## Error Handling Strategy
 *
 * **Server actions return errors, client handles logout:**
 * - Server actions detect invalid tokens and return error objects
 * - Client components check for errors and trigger client-side logout
 * - This approach fixes logout bugs where server-initiated logout didn't work
 * - Ensures proper cleanup of both server session and client Firebase state
 *
 * ## Why This Architecture?
 *
 * 1. **Edge Runtime Compatibility**: Middleware can't use full Firebase Admin SDK
 * 2. **Separation of Concerns**: Routing (middleware) vs security (server actions)
 * 3. **Avoid Redirect Loops**: Middleware allows /admin login page without tokens
 * 4. **Maintainability**: Single validation helper vs duplicated code
 * 5. **Proper Error Handling**: Client-side logout works correctly
 */

'use server'

import {
  initializeApp,
  cert,
  ServiceAccount,
  getApps,
} from 'firebase-admin/app'
import { getAuth, type Auth } from 'firebase-admin/auth'
import { getAppCheck } from 'firebase-admin/app-check'

import serviceAccount from '../firebase.json' with { type: 'json' }
import { getIdToken } from './auth'
import { getAppCheckToken } from './appCheck'

/**
 * Result of token validation
 */
export interface ValidationResult {
  /** Whether both tokens are valid */
  valid: boolean
  /** Error message if validation failed */
  error?: string
}

/**
 * Token pair to validate
 */
export interface Tokens {
  /** Firebase ID token for user authentication */
  idToken?: string
  /** Firebase App Check token for app integrity */
  appCheckToken?: string
}

/**
 * Authentication error returned by server actions when token validation fails
 *
 * This is the standard error format for all authenticated server actions.
 * Client components should check for this error type and handle logout.
 *
 * **Usage in server actions:**
 * ```typescript
 * const authOrError = await getAuthenticatedAuth()
 * if ('error' in authOrError) {
 *   return authOrError  // Returns AuthenticationError
 * }
 * ```
 *
 * **Usage in client components:**
 * ```typescript
 * const result = await myServerAction()
 * if ('error' in result) {
 *   // Handle authentication error (trigger logout)
 *   console.error(result.error, result.details)
 * }
 * ```
 */
export interface AuthenticationError {
  /** Error type identifier */
  error: 'INVALID_TOKENS'
  /** Detailed error message for logging/debugging */
  details?: string
}

/**
 * Validates Firebase ID token and App Check token
 *
 * This function provides centralized token validation for all server actions.
 * It verifies both the user's identity (idToken) and app integrity (appCheckToken)
 * using the Firebase Admin SDK.
 *
 * **Why in server actions, not middleware?**
 * - Middleware runs in Edge runtime with limited Firebase Admin SDK support
 * - Server actions run in Node.js runtime with full Firebase Admin SDK
 * - This ensures reliable token signature verification
 *
 * **Usage Pattern:**
 * ```typescript
 * export async function myServerAction() {
 *   const tokens = {
 *     idToken: await getIdToken(),
 *     appCheckToken: await getAppCheckToken()
 *   }
 *
 *   const validation = await validateTokens(tokens)
 *   if (!validation.valid) {
 *     return { error: 'INVALID_TOKENS', details: validation.error }
 *   }
 *
 *   // Proceed with authenticated operation
 *   return performOperation()
 * }
 * ```
 *
 * **Error Handling:**
 * - Returns `{ valid: false, error: '...' }` on validation failure
 * - Does NOT redirect (server actions can't reliably trigger client logout)
 * - Client should check for errors and trigger logout via useUserSession hook
 *
 * @param tokens - The token pair to validate
 * @returns Validation result with success status and optional error message
 */
export async function validateTokens(
  tokens: Tokens
): Promise<ValidationResult> {
  // Initialize Firebase Admin if not already initialized
  if (!getApps().length) {
    initializeApp({
      credential: cert(serviceAccount as ServiceAccount),
      databaseURL: 'https://refitted-361ee.firebaseio.com',
    })
  }

  // Validate App Check token
  if (!tokens.appCheckToken) {
    console.error('No app check token present')
    return { valid: false, error: 'Missing App Check token' }
  }

  try {
    await getAppCheck().verifyToken(tokens.appCheckToken)
    console.debug('App check token verified')
  } catch (error) {
    console.error('Failed to verify appcheck token', error)
    return {
      valid: false,
      error: `App Check verification failed: ${error instanceof Error ? error.message : String(error)}`,
    }
  }

  // Validate ID token
  if (!tokens.idToken) {
    console.error('No id token present')
    return { valid: false, error: 'Missing ID token' }
  }

  try {
    await getAuth().verifyIdToken(tokens.idToken)
    console.debug('Id token verified')
  } catch (error) {
    console.error('Failed to verify ID token', error)
    return {
      valid: false,
      error: `ID token verification failed: ${error instanceof Error ? error.message : String(error)}`,
    }
  }

  return { valid: true }
}

/**
 * Initializes Firebase Admin SDK if not already initialized
 *
 * This is safe to call multiple times - it only initializes once.
 */
function ensureFirebaseAdminInitialized() {
  if (!getApps().length) {
    initializeApp({
      credential: cert(serviceAccount as ServiceAccount),
      databaseURL: 'https://refitted-361ee.firebaseio.com',
    })
  }
}

/**
 * Gets an authenticated Firebase Auth instance
 *
 * This is the recommended way to access Firebase Admin Auth in server actions.
 * It combines token validation and initialization into a single call.
 *
 * **What it does:**
 * 1. Retrieves idToken and appCheckToken from session
 * 2. Validates both tokens using Firebase Admin SDK
 * 3. Initializes Firebase Admin if needed
 * 4. Returns authenticated Auth instance
 *
 * **Usage Pattern:**
 * ```typescript
 * export async function myServerAction() {
 *   const authOrError = await getAuthenticatedAuth()
 *   if ('error' in authOrError) {
 *     return authOrError // Return AuthenticationError to client
 *   }
 *
 *   // Use auth instance for authenticated operations
 *   const users = await authOrError.listUsers(1000)
 *   return users
 * }
 * ```
 *
 * **Why use this instead of validateTokens() + getAuth()?**
 * - Eliminates boilerplate: no need to manually validate, check errors, and initialize
 * - Consistent pattern across all server actions
 * - Single source of truth for authentication flow
 *
 * **Error Handling:**
 * Returns AuthenticationError if validation fails.
 *
 * @returns Firebase Auth instance or AuthenticationError
 */
export async function getAuthenticatedAuth(): Promise<
  Auth | AuthenticationError
> {
  // Get tokens from session
  const tokens = {
    idToken: await getIdToken(),
    appCheckToken: await getAppCheckToken(),
  }

  // Validate tokens
  const validation = await validateTokens(tokens)
  if (!validation.valid) {
    return {
      error: 'INVALID_TOKENS',
      details: validation.error,
    }
  }

  // Ensure Firebase Admin is initialized
  ensureFirebaseAdminInitialized()

  // Return authenticated Auth instance
  return getAuth()
}
