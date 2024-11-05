import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

export const config = {
  matcher: ['/admin/(.+)'],
}

export function middleware(request: NextRequest) {
  const cookie = request.cookies.get('session')

  const session = JSON.parse(cookie?.value ?? '{}')
  if (!session.isAdmin) {
    // TODO 404/logout? Otherwise infinite loop
    return NextResponse.redirect(new URL('/admin', request.url))
  }
  return NextResponse.next()
}
