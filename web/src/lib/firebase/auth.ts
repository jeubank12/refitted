import { startTransition, useCallback, useEffect, useState } from 'react'

import { useRouter } from 'next/navigation'

import {
  browserSessionPersistence,
  getAuth,
  GoogleAuthProvider,
  setPersistence,
  signInWithPopup,
  User,
} from 'firebase/auth'
import { getToken } from 'firebase/app-check'

import { app, useAppCheck } from './firebaseApp'
import { login, logout } from 'src/lib/firebase/actions/auth'

const provider = new GoogleAuthProvider()

// Turns SDK error codes and login()'s short error codes into messages that tell
// the user what happened, without naming the underlying mechanism (App Check,
// reCAPTCHA, etc.) to a client we don't control. Debugging detail belongs in
// server/console logs, not this string — see web/README.md for the App Check
// debug-token setup this commonly fails on locally.
function describeLoginError(err: unknown): string {
  const code = (err as { code?: unknown } | null)?.code
  if (typeof code === 'string') {
    if (code.startsWith('appCheck/')) {
      return 'Verification failed. Please try the security check again.'
    }
    if (code === 'auth/popup-closed-by-user') {
      return 'Sign-in was cancelled.'
    }
    if (code === 'auth/popup-blocked') {
      return 'Your browser blocked the sign-in popup. Allow popups for this site and try again.'
    }
  }
  const message = err instanceof Error ? err.message : undefined
  switch (message) {
    case 'not-authorized':
      return 'This Google account does not have admin access.'
    case 'stale-auth':
      return 'Your sign-in was too old to start a session — please try again.'
    case 'app-check-failed':
      return 'Verification failed. Please try the security check again.'
    default:
      return 'Login failed. Please try again.'
  }
}

// login()'s own coded refusals — the server correctly did its job, so these
// aren't bugs. console.warn (not .error) so Next's dev overlay doesn't treat
// an expected "not an admin"/"stale session" outcome as a crash.
const EXPECTED_LOGIN_REFUSALS = new Set(['not-authorized', 'stale-auth', 'app-check-failed'])

function logLoginFailure(err: unknown) {
  const code = (err as { code?: unknown } | null)?.code
  const message = err instanceof Error ? err.message : undefined
  const expected =
    (typeof code === 'string' &&
      (code.startsWith('appCheck/') ||
        code === 'auth/popup-closed-by-user' ||
        code === 'auth/popup-blocked')) ||
    (message !== undefined && EXPECTED_LOGIN_REFUSALS.has(message))
  if (expected) {
    console.warn('Login refused', err)
  } else {
    console.error('Login failed', err)
  }
}

export const useLogin = () => {
  const [error, setError] = useState<string | null>(null)
  const appCheck = useAppCheck()
  const router = useRouter()

  const doLogin = useCallback(() => {
    const auth = getAuth(app)
    setPersistence(auth, browserSessionPersistence)
    signInWithPopup(auth, provider).then(
      success => {
        startTransition(async () => {
          try {
            const credential = GoogleAuthProvider.credentialFromResult(success)
            if (!credential?.idToken) {
              throw new Error('Failed to get idToken')
            }
            await auth.authStateReady()
            if (!auth.currentUser) {
              throw new Error('not logged in')
            }
            const idToken = await auth.currentUser.getIdToken()
            const check = appCheck.current
            if (!check) {
              throw new Error('App Check not initialized')
            }
            const result = await getToken(check)
            const loginResult = await login(idToken, result.token)
            if (loginResult?.error) {
              throw new Error(loginResult.error)
            }
            // Navigate explicitly rather than relying on login() to redirect —
            // a redirect() thrown from a directly-invoked (non-<form>) action
            // isn't reliably picked up by the router in this Next version.
            router.push('/admin/users')
          } catch (err) {
            // Full detail to the console for debugging; only a generic,
            // mechanism-agnostic message goes to setError for display.
            logLoginFailure(err)
            // Tear down the real session, not just the client SDK's mirror of it —
            // login() may have already set the session cookie before failing later.
            await logout()
            await auth.signOut()
            setError(describeLoginError(err))
          }
        })
      },
      error => {
        logLoginFailure(error)
        setError(describeLoginError(error))
      }
    )
  }, [appCheck, router])
  return { error, doLogin }
}

export const useUserSession = () => {
  const [firebaseUser, setFirebaseUser] = useState<User | null>()
  const router = useRouter()

  useEffect(() => {
    const auth = getAuth(app)
    return auth.onAuthStateChanged(user => {
      console.log('user change', user)
      setFirebaseUser(user)
    })
  }, [])

  const doLogout = useCallback(async () => {
    console.log('Logging out')
    const auth = getAuth(app)
    await logout()
    await auth.authStateReady()
    await auth.signOut()
    console.debug('Logged out, redirecting to /admin')
    router.push('/admin')
  }, [router])

  return { logout: doLogout, firebaseUser }
}
