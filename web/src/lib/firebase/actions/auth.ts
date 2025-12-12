'use server'

import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'

import { getAuth } from 'firebase-admin/auth'
import {
  cert,
  getApps,
  initializeApp,
  ServiceAccount,
} from 'firebase-admin/app'

import serviceAccount from '../firebase.json' with { type: 'json' }
import { getAppCheck } from 'firebase-admin/app-check'

function ensureInitialized() {
  // Initialize Firebase Admin if not already initialized
  if (!getApps().length) {
    initializeApp({
      credential: cert(serviceAccount as ServiceAccount),
      databaseURL: 'https://refitted-361ee.firebaseio.com',
    })
  }
}

export async function login(idToken: string, appCheckToken: string) {
  ensureInitialized()

  // Validate appCheckToken before creating session
  const validationError = await validateAppCheck(appCheckToken)
  if (validationError) {
    console.error('Login failed', validationError)
    return
  }

  const expiresIn = 60 * 60 * 1000
  const session = await getAuth().createSessionCookie(idToken, {
    expiresIn,
  })

  const cookieStore = await cookies()
  cookieStore.set('session', session, {
    httpOnly: true,
    secure: true,
    maxAge: expiresIn,
  })
  redirect('/admin/users')
}

export async function logout() {
  ensureInitialized()

  console.log('Logout')
  const cookieStore = await cookies()
  const session = cookieStore.get('session')?.value ?? ''
  cookieStore.delete('session')
  try {
    const decodedClaims = await getAuth().verifySessionCookie(session)
    await getAuth().revokeRefreshTokens(decodedClaims.sub)
  } catch (error) {
    console.error('Error during logout token revocation', error)
  }
  redirect('/admin')
}

async function validateAppCheck(
  appCheckToken: string
): Promise<string | undefined> {
  try {
    await getAppCheck().verifyToken(appCheckToken)
    console.debug('App check token verified')
  } catch (error) {
    console.error('Failed to verify appcheck token', error)
    return `App Check verification failed: ${error instanceof Error ? error.message : String(error)}`
  }
}

export async function getAuthenticatedAuth() {
  ensureInitialized()

  const cookieStore = await cookies()
  const session = cookieStore.get('session')?.value ?? ''
  const decodedClaims = await getAuth().verifySessionCookie(session, true)
  if (decodedClaims.admin === true) {
    return getAuth()
  }
  return Promise.reject('User is not an admin')
}
