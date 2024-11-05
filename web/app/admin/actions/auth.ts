'use server'

import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'

import { initializeServerApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'

import { firebaseConfig } from 'src/lib/firebase/firebaseConfig'

async function createSession(isAdmin: boolean) {
  const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
  const sessionContent = JSON.stringify({
    isAdmin,
  })
  cookies().set('session', sessionContent, {
    httpOnly: true,
    secure: true,
    expires: expiresAt,
    sameSite: 'lax',
    path: '/',
  })
}

export async function login(idToken: string) {
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

  // TODO 404?
  if (!auth.currentUser) return
  const idTokenResult = await auth.currentUser.getIdTokenResult()
  createSession(!!idTokenResult.claims?.admin)
  console.log('Logged in', auth.currentUser.email, {
    isAdmin: !!idTokenResult.claims?.admin,
  })
  return redirect('/admin/users')
}

export async function logout() {
  await cookies().delete('session')
  return redirect('/admin')
}
