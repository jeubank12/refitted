import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

/**
 * Protect all /admin routes (including /admin itself for redirect logic)
 */
export const config = {
  matcher: ['/admin/:path*'],
}

/**
 * Middleware function to protect admin routes with JWT verification
 */
export async function proxy(request: NextRequest) {
  const cookie = request.cookies.get('session')

  if (cookie?.value) {
    // for now, just allow if cookie exists
    // edge runtime doesn't support firebase-admin, so we can't verify session cookies here
    // we will need to write our own validation

    // const payload = await verifySessionJwt(cookie.value)
    // if (payload?.isAdmin) {
    //   // Special case for /admin login page
    //   if (request.nextUrl.pathname === '/admin') {
    //     // Already logged in with valid JWT, redirect to users page
    //     console.debug('User already logged in, redirecting to /admin/users')
    //     return NextResponse.redirect(new URL('/admin/users', request.url))
    //   }
    //   // Valid admin session, allow through
    //   console.debug('Valid admin session, allowing access to', request.url)
    //   return NextResponse.next()
    // } else if (payload) {
    //   console.debug('User is not admin, redirecting to /admin')
    //   return NextResponse.redirect(new URL('/admin', request.url))
    // }
    return NextResponse.next()
  }

  if (request.nextUrl.pathname === '/admin') {
    return NextResponse.next()
  }

  // No valid session, allow through to show login page
  console.debug('No valid session, showing login page')
  return NextResponse.redirect(new URL('/admin', request.url))
}
