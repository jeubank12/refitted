import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

import { adminAuth } from 'src/lib/firebase/admin'

/**
 * Protect all /admin routes (including /admin itself for redirect logic)
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
  const isLoginPage = request.nextUrl.pathname === '/admin'

  if (!cookie?.value) {
    if (isLoginPage) {
      return NextResponse.next()
    }
    console.debug('No session cookie, redirecting to /admin')
    return NextResponse.redirect(new URL('/admin', request.url))
  }

  try {
    const payload = await adminAuth().verifySessionCookie(cookie.value)
    if (payload.admin === true) {
      if (isLoginPage) {
        console.debug('User already logged in, redirecting to /admin/users')
        return NextResponse.redirect(new URL('/admin/users', request.url))
      }
      return NextResponse.next()
    }
    console.debug('User is not admin, redirecting to /admin')
  } catch (error) {
    console.debug('Session cookie failed verification', error)
  }

  const response = NextResponse.redirect(new URL('/admin', request.url))
  response.cookies.delete('session')
  return response
}
