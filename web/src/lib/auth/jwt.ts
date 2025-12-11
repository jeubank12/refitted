/**
 * JWT Session Token Utilities
 *
 * This module provides JWT signing and verification for session cookies.
 * Uses jose library for Edge Runtime compatibility.
 *
 * ## Security
 *
 * - Uses HS256 (HMAC with SHA-256) for signing
 * - Secret loaded from JWT_SECRET environment variable
 * - Tokens expire after 7 days (matches cookie expiration)
 * - Payload includes user identity and Firebase tokens
 *
 * ## Usage
 *
 * **Signing a session:**
 * ```typescript
 * const jwt = await signSessionJwt({
 *   userId: user.uid,
 *   email: user.email,
 *   isAdmin: true,
 *   idToken: firebaseIdToken,
 *   appCheckToken: firebaseAppCheckToken
 * })
 * ```
 *
 * **Verifying a session:**
 * ```typescript
 * const payload = await verifySessionJwt(jwtString)
 * if (payload) {
 *   // JWT is valid and signature verified
 *   console.log(payload.email, payload.isAdmin)
 * }
 * ```
 */

import { SignJWT, jwtVerify } from 'jose'

/**
 * Session payload structure
 *
 * This is the data stored in the JWT and cryptographically signed.
 */
export interface SessionPayload {
  /** Firebase user ID (UID) */
  userId: string
  /** User email address */
  email: string
  /** Whether user has admin privileges */
  isAdmin: boolean
  /** Firebase ID token (for Firebase Admin SDK calls) */
  idToken: string
  /** Firebase App Check token (for app integrity verification) */
  appCheckToken: string
}

/**
 * Get JWT secret from environment
 *
 * Throws if JWT_SECRET is not set to ensure application fails fast
 * rather than running without security.
 *
 * @throws {Error} If JWT_SECRET environment variable is not set
 * @returns Secret key as Uint8Array for jose library
 */
function getJwtSecret(): Uint8Array {
  const secret = process.env.JWT_SECRET
  if (!secret) {
    throw new Error(
      'JWT_SECRET environment variable is not set. ' +
        'Generate one with: openssl rand -base64 32'
    )
  }
  return new TextEncoder().encode(secret)
}

/**
 * Signs a session payload into a JWT
 *
 * Creates a signed JWT with:
 * - Algorithm: HS256 (HMAC with SHA-256)
 * - Expiration: 7 days (matches cookie expiration)
 * - Issued at: Current timestamp
 *
 * **Security:**
 * - Payload is signed with JWT_SECRET, preventing tampering
 * - Expiration is enforced by jose library
 * - Any modification to payload invalidates signature
 *
 * @param payload - Session data to sign
 * @returns Signed JWT string (three base64url parts separated by dots)
 * @throws {Error} If JWT_SECRET is not set
 */
export async function signSessionJwt(
  payload: SessionPayload
): Promise<string> {
  const secret = getJwtSecret()

  const jwt = await new SignJWT(payload)
    .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
    .setIssuedAt()
    .setExpirationTime('7d') // Match cookie expiration
    .sign(secret)

  return jwt
}

/**
 * Verifies and decodes a session JWT
 *
 * Performs:
 * 1. Signature verification using JWT_SECRET
 * 2. Expiration check
 * 3. Payload structure validation
 *
 * **Security:**
 * - Returns null for any invalid/expired/tampered tokens
 * - Does not throw exceptions (easier error handling)
 * - Validates payload structure to ensure all required fields present
 *
 * @param token - JWT string to verify
 * @returns Decoded payload if valid, null if invalid/expired/malformed
 */
export async function verifySessionJwt(
  token: string
): Promise<SessionPayload | null> {
  try {
    const secret = getJwtSecret()
    const { payload } = await jwtVerify(token, secret, {
      algorithms: ['HS256'],
    })

    // Validate payload structure
    // This ensures the JWT contains all required fields and has correct types
    if (
      typeof payload.userId !== 'string' ||
      typeof payload.email !== 'string' ||
      typeof payload.isAdmin !== 'boolean' ||
      typeof payload.idToken !== 'string' ||
      typeof payload.appCheckToken !== 'string'
    ) {
      console.error('Invalid JWT payload structure', payload)
      return null
    }

    return payload as SessionPayload
  } catch (error) {
    // Jose throws errors for:
    // - Invalid signature
    // - Expired token
    // - Malformed JWT
    // - Algorithm mismatch
    console.error('JWT verification failed:', error)
    return null
  }
}

/**
 * Checks if a session cookie value is a JWT (vs plain JSON)
 *
 * JWTs have the format: `header.payload.signature`
 * Each part is base64url encoded (A-Z, a-z, 0-9, -, _)
 *
 * This allows detecting legacy plain JSON sessions for migration.
 *
 * **Example:**
 * ```typescript
 * isJwt('eyJhbGc...') // true (JWT)
 * isJwt('{"isAdmin":true}') // false (plain JSON)
 * isJwt('') // false (empty)
 * ```
 *
 * @param value - Cookie value to check
 * @returns True if value looks like a JWT, false otherwise
 */
export function isJwt(value: string): boolean {
  // JWTs have three base64url parts separated by dots
  // Base64url characters: A-Z, a-z, 0-9, -, _
  return /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(value)
}
