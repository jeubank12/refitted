'use server'

import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'

import { initializeServerApp } from 'firebase/app'
import { getAuth, User } from 'firebase/auth'

import { firebaseConfig } from 'src/lib/firebase/firebaseConfig'

async function writeSession(idToken: string, isAdmin: boolean) {
  const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
  const sessionContent = JSON.stringify({
    isAdmin,
    // TODO encrypt
    idToken,
  })
  cookies().set('session', sessionContent, {
    httpOnly: true,
    secure: true,
    expires: expiresAt,
    sameSite: 'lax',
    path: '/',
  })
}

async function getAppForUser(idToken: string) {
  const firebaseServerApp = initializeServerApp(
    firebaseConfig,
    idToken
      ? {
          authIdToken: idToken,
        }
      : {}
  )

  const auth = getAuth(firebaseServerApp)
  await auth.authStateReady()

  return { firebaseServerApp, currentUser: auth.currentUser }
}

export async function login(idToken: string) {
  const { currentUser } = await getAppForUser(idToken)

  if (!currentUser) return
  return createSession(currentUser)
}

async function createSession(user: User) {
  const idTokenResult = await user.getIdTokenResult()
  writeSession(idTokenResult.token, !!idTokenResult.claims?.admin)
  console.log('Logged in', user.email, {
    isAdmin: !!idTokenResult.claims?.admin,
  })
  return redirect('/admin/users')
}

export async function refreshSession(idToken?: string) {
  if (!idToken) {
    return await logout()
  }
  const { currentUser } = await getAppForUser(idToken)

  const sessionIdToken = getIdToken()
  if (!currentUser) {
    // if there is no session either, go to login
    if (!(await sessionIdToken)) return redirect('/admin')
    return
  }
  if (!(await sessionIdToken)) {
    console.log()
    // if there was no session, treat this as login
    return createSession(currentUser)
  }
  const idTokenResult = await currentUser.getIdTokenResult()
  writeSession(idToken, !!idTokenResult.claims?.admin)
  console.log('Refreshed', currentUser.email, {
    isAdmin: !!idTokenResult.claims?.admin,
  })
}

export async function logout() {
  console.log('Logout')
  await cookies().delete('session')
  return redirect('/admin')
}

export async function getIdToken(): Promise<string | undefined> {
  const cookie = cookies().get('session')
  const session = JSON.parse(cookie?.value ?? '{}')
  return Promise.resolve(session?.idToken)
}

export async function getUserInfo() {
  const idToken = await getIdToken()
  if (!idToken) return
  const { currentUser } = await getAppForUser(idToken)
  return currentUser
    ? {
        displayName: currentUser.displayName ?? undefined,
        email: currentUser.email ?? undefined,
      }
    : undefined
}
