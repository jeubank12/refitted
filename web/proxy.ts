/**
 * Authentication Middleware
 *
 * This middleware provides JWT-based authentication for admin routes.
 * It runs in Next.js Edge runtime and performs signature verification.
 *
 * ## Updated Security Architecture
 *
 * **Layer 1 (This Middleware) - JWT Verification:**
 * - Verifies JWT signature using jose library
 * - Extracts and trusts isAdmin flag from signed payload
 * - Fast Edge Runtime execution
 * - Purpose: Routing protection with cryptographic verification
 *
 * **Layer 2 (Server Actions) - Conditional Firebase Validation:**
 * - Only validates Firebase tokens when calling Firebase Admin SDK
 * - getAuthenticatedAuth() → validates tokens for Firebase operations
 * - Other actions → trust JWT payload (already verified by middleware)
 * - Purpose: Firebase-specific security enforcement
 *
 * ## Route Protection Logic
 *
 * - `/admin` (exact) → ALLOWED (login page, needs no tokens)
 * - `/admin/*` → PROTECTED (requires valid JWT with isAdmin=true)
 *
 * This avoids redirect loops where invalid tokens would redirect to /admin,
 * but middleware would block /admin, creating an infinite loop.
 *
 * ## Error Handling
 *
 * If JWT is missing, invalid signature, expired, or user is not admin:
 * - Redirect to /admin login page
 * - User will see login form and can re-authenticate
 *
 * Legacy plain JSON sessions:
 * - Detected by isJwt() check
 * - Redirect to /admin for re-login
 * - User gets JWT session on next login
 *
 * ## Security Improvements
 *
 * - Before: Only checked token presence (no signature validation)
 * - After: Cryptographically verifies JWT signature
 * - isAdmin flag now trusted (can't be tampered with)
 * - Reduces Firebase Admin SDK calls (better performance)
 */

import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'
import { verifySessionJwt, isJwt } from './src/lib/auth/jwt'

/**
 * Protect all /admin routes (including /admin itself for redirect logic)
 */
export const config = {
  matcher: ['/admin/:path*'],
}

/**
 * Middleware function to protect admin routes with JWT verification
 *
 * Performs cryptographic verification:
 * - JWT signature validation
 * - Expiration check
 * - Admin privilege check
 *
 * **Special handling for /admin login page:**
 * - If user already has valid JWT → redirect to /admin/users
 * - If no valid JWT → allow through to show login page
 *
 * **Security:**
 * - Verifies JWT signature with HMAC-SHA256
 * - Rejects legacy plain JSON sessions
 * - Trusts isAdmin flag from verified JWT payload
 */
export async function proxy(request: NextRequest) {
  const cookie = request.cookies.get('session')

  // Special case for /admin login page
  if (request.nextUrl.pathname === '/admin') {
    if (cookie?.value && isJwt(cookie.value)) {
      const payload = await verifySessionJwt(cookie.value)
      if (payload?.isAdmin) {
        // Already logged in with valid JWT, redirect to users page
        console.debug('User already logged in, redirecting to /admin/users')
        return NextResponse.redirect(new URL('/admin/users', request.url))
      }
    }
    // No valid session, allow through to show login page
    console.debug('No valid session, showing login page')
    return NextResponse.next()
  }

  // For all other /admin/* routes, require valid JWT
  if (!cookie?.value) {
    console.debug('No session cookie, redirecting to login')
    return NextResponse.redirect(new URL('/admin', request.url))
  }

  // Check if JWT format
  if (!isJwt(cookie.value)) {
    console.warn(
      'Legacy plain JSON session detected in middleware, redirecting to login'
    )
    return NextResponse.redirect(new URL('/admin', request.url))
  }

  // Verify JWT signature and decode payload
  const payload = await verifySessionJwt(cookie.value)

  if (!payload) {
    console.warn('Invalid JWT signature or expired token, redirecting to login')
    return NextResponse.redirect(new URL('/admin', request.url))
  }

  // Check admin privileges from verified JWT
  if (!payload.isAdmin) {
    console.warn('User does not have admin privileges:', payload.email)
    return NextResponse.redirect(new URL('/admin', request.url))
  }

  console.debug('JWT verified, admin access granted:', payload.email)

  // Allow request to proceed
  return NextResponse.next()
}
