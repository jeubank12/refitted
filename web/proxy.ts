import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

import { adminAuth } from 'src/lib/firebase/admin'
import { ADMIN_BASE_PATH } from 'src/lib/adminPath'

/**
 * Protect all admin routes (including the base path itself, for redirect logic).
 * The matcher below must stay a literal, static value -- Next.js can't
 * statically analyze a matcher built from an imported/dynamic constant, so this
 * is refitted's own default (/admin) and is not shared with litus-animae's
 * build, which declares its own matcher locally instead. Everything else in
 * this file uses ADMIN_BASE_PATH and is shared.
 */
export const config = {
  matcher: ['/admin/:path*'],
}

/**
 * Middleware function to protect admin routes with session cookie verification.
 * This is routing/UX only; every server action independently re-verifies the
 * session via getAuthenticatedAuth() as defense in depth.
 */
export async function proxy(request: NextRequest) {
  const cookie = request.cookies.get('session')
  const isLoginPage = request.nextUrl.pathname === ADMIN_BASE_PATH

  if (!cookie?.value) {
    if (isLoginPage) {
      return NextResponse.next()
    }
    console.debug(`No session cookie, redirecting to ${ADMIN_BASE_PATH}`)
    return NextResponse.redirect(new URL(ADMIN_BASE_PATH, request.url))
  }

  try {
    const payload = await adminAuth().verifySessionCookie(cookie.value)
    if (payload.admin === true) {
      if (isLoginPage) {
        console.debug(`User already logged in, redirecting to ${ADMIN_BASE_PATH}/users`)
        return NextResponse.redirect(new URL(`${ADMIN_BASE_PATH}/users`, request.url))
      }
      return NextResponse.next()
    }
    console.debug(`User is not admin, redirecting to ${ADMIN_BASE_PATH}`)
  } catch (error) {
    console.debug('Session cookie failed verification', error)
  }

  const response = NextResponse.redirect(new URL(ADMIN_BASE_PATH, request.url))
  response.cookies.delete('session')
  return response
}
