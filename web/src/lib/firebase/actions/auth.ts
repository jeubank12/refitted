'use server'

import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'

import { adminAuth, adminAppCheck } from '../admin'

export async function login(
  idToken: string,
  appCheckToken: string
): Promise<{ error?: string } | void> {
  // Validate appCheckToken before creating session
  const validationError = await validateAppCheck(appCheckToken)
  if (validationError) {
    console.error('Login failed', validationError)
    return { error: 'app-check-failed' }
  }

  const decoded = await adminAuth().verifyIdToken(idToken)
  if (decoded.admin !== true) {
    console.warn('Login refused: not an admin', decoded.uid)
    return { error: 'not-authorized' }
  }
  // Require a recent sign-in to mint a session (mitigates replay of an old stolen ID token)
  if (Date.now() / 1000 - decoded.auth_time > 5 * 60) {
    return { error: 'stale-auth' }
  }

  const SESSION_DURATION_MS = 5 * 24 * 60 * 60 * 1000 // 5 days (max allowed is 14)
  const session = await adminAuth().createSessionCookie(idToken, {
    expiresIn: SESSION_DURATION_MS,
  })

  const cookieStore = await cookies()
  cookieStore.set('session', session, {
    httpOnly: true,
    secure: true,
    sameSite: 'lax',
    path: '/',
    maxAge: SESSION_DURATION_MS / 1000,
  })
  redirect('/admin/users')
}

export async function logout() {
  console.log('Logout')
  const cookieStore = await cookies()
  const session = cookieStore.get('session')?.value ?? ''
  cookieStore.delete('session')
  try {
    const decodedClaims = await adminAuth().verifySessionCookie(session)
    await adminAuth().revokeRefreshTokens(decodedClaims.sub)
  } catch (error) {
    console.error('Error during logout token revocation', error)
  }
}

export async function serverLogout() {
  console.log('Server Logout')
  const cookieStore = await cookies()
  const session = cookieStore.get('session')?.value ?? ''
  try {
    const decodedClaims = await adminAuth().verifySessionCookie(session)
    await adminAuth().revokeRefreshTokens(decodedClaims.sub)
  } catch (error) {
    console.error('Error during logout token revocation', error)
  }
  // server logout needs to tell the client to also logout
  redirect('/admin/logout')
}

async function validateAppCheck(
  appCheckToken: string
): Promise<string | undefined> {
  try {
    await adminAppCheck().verifyToken(appCheckToken)
    console.debug('App check token verified')
  } catch (error) {
    console.error('Failed to verify appcheck token', error)
    return `App Check verification failed: ${error instanceof Error ? error.message : String(error)}`
  }
}

export async function getAuthenticatedAuth() {
  const cookieStore = await cookies()
  const session = cookieStore.get('session')?.value ?? ''
  const decodedClaims = await adminAuth().verifySessionCookie(session, true)
  if (decodedClaims.admin === true) {
    return adminAuth()
  }
  return Promise.reject('User is not an admin')
}
