import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

export const config = {
  matcher: ['/admin/(.+)'],
}

export function middleware(request: NextRequest) {
  const cookie = request.cookies.get('session')
  console.debug('middleware', request.url, cookie) // => { name: 'nextjs', value: 'fast', Path: '/' }

  const session = JSON.parse(cookie?.value ?? '{}')
  if (!session.isAdmin) {
    return NextResponse.redirect(new URL('/admin', request.url))
  }
  return NextResponse.next()
}
