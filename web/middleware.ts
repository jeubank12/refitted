/**
 * Authentication Middleware
 *
 * This middleware provides lightweight routing protection for admin pages.
 * It runs in Next.js Edge runtime and performs fast token presence checks.
 *
 * ## Hybrid Security Architecture
 *
 * This is **Layer 1** of our two-layer security model:
 *
 * **Layer 1 (This Middleware):**
 * - Checks that session cookie exists
 * - Verifies tokens are present (not null/undefined)
 * - Verifies isAdmin flag is set
 * - Does NOT validate token signatures (Edge runtime limitation)
 * - Purpose: Fast routing decisions, prevent unnecessary requests
 *
 * **Layer 2 (Server Actions):**
 * - Full token signature validation using Firebase Admin SDK
 * - See: src/lib/firebase/actions/validateTokens.ts
 * - Purpose: Actual security enforcement at data access layer
 *
 * ## Route Protection Logic
 *
 * - `/admin` (exact) → ALLOWED (login page, needs no tokens)
 * - `/admin/*` → PROTECTED (requires valid session with tokens)
 *
 * This avoids redirect loops where invalid tokens would redirect to /admin,
 * but middleware would block /admin, creating an infinite loop.
 *
 * ## Error Handling
 *
 * If tokens are missing or user is not admin:
 * - Redirect to /admin login page
 * - User will see login form and can re-authenticate
 *
 * If tokens are present but INVALID (expired, tampered, etc.):
 * - Middleware allows request through (only checks presence)
 * - Server actions will detect invalid tokens and return errors
 * - Client-side code will trigger logout
 *
 * This separation ensures proper logout flow without redirect loops.
 */

import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

/**
 * Protect all /admin/* routes (but not /admin itself)
 */
export const config = {
  matcher: ['/admin/(.+)'],
}

/**
 * Session cookie structure
 */
interface Session {
  isAdmin?: boolean
  idToken?: string
  appCheckToken?: string
}

/**
 * Middleware function to protect admin routes
 *
 * Performs lightweight checks:
 * - Session cookie exists
 * - Both tokens are present
 * - User has admin flag
 *
 * Note: Does NOT validate token signatures (see validateTokens.ts for that)
 */
export function middleware(request: NextRequest) {
  const cookie = request.cookies.get('session')

  // Parse session cookie (empty object if not present or empty string)
  // Use || instead of ?? to handle empty strings (after cookie deletion)
  const session: Session = JSON.parse(cookie?.value || '{}')

  // Check if user has admin privileges and both tokens are present
  const hasAdminAccess =
    session.isAdmin && session.idToken && session.appCheckToken

  if (!hasAdminAccess) {
    // Redirect to login page
    // Note: /admin is not matched by this middleware, so no redirect loop
    return NextResponse.redirect(new URL('/admin', request.url))
  }

  // Allow request to proceed
  // Note: Server actions will perform full token validation
  return NextResponse.next()
}
