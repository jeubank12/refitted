'use server'

import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'

import { initializeServerApp } from 'firebase/app'
import { getAuth, User } from 'firebase/auth'

import { firebaseConfig } from '../firebaseConfig'
import { getAppCheckToken } from './appCheck'
import { validateTokens } from './validateTokens'

/**
 * Writes authentication session to cookie
 *
 * Creates an HTTP-only session cookie containing:
 * - isAdmin: Whether user has admin privileges
 * - idToken: Firebase ID token for user authentication
 * - appCheckToken: Firebase App Check token for app integrity
 *
 * **Session Cookie Structure:**
 * ```json
 * {
 *   "isAdmin": boolean,
 *   "idToken": string,
 *   "appCheckToken": string
 * }
 * ```
 *
 * **Security Properties:**
 * - httpOnly: Prevents JavaScript access (XSS protection)
 * - secure: HTTPS only
 * - sameSite: 'lax' (CSRF protection)
 * - expires: 7 days
 *
 * @param idToken - Firebase ID token
 * @param appCheckToken - Firebase App Check token
 * @param isAdmin - Whether user has admin privileges
 */
async function writeSession(
  idToken: string,
  appCheckToken: string,
  isAdmin: boolean
) {
  const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
  const sessionContent = JSON.stringify({
    isAdmin,
    // TODO encrypt
    idToken,
    appCheckToken,
  })
  const requestCookies = await cookies()
  requestCookies.set('session', sessionContent, {
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

/**
 * Logs in a user with Firebase ID token and App Check token
 *
 * **Security:**
 * - Validates idToken by creating server app
 * - Validates appCheckToken before storing in session
 *
 * @param idToken - Firebase ID token from client authentication
 * @param appCheckToken - Firebase App Check token from client
 * @returns Redirect to admin panel or undefined on failure
 */
export async function login(idToken: string, appCheckToken: string) {
  // Validate idToken by getting user
  const { currentUser } = await getAppForUser(idToken)
  if (!currentUser) return

  // Validate appCheckToken before creating session
  const validation = await validateTokens({ idToken, appCheckToken })
  if (!validation.valid) {
    console.error('Login failed - invalid tokens:', validation.error)
    return
  }

  return createSession(currentUser, appCheckToken)
}

/**
 * Creates a new session for an authenticated user
 *
 * **Security Note:**
 * This function assumes tokens have already been validated by the caller.
 * It's called from login() and refreshSession(), both of which validate tokens first.
 *
 * @param user - Authenticated Firebase user
 * @param appCheckToken - App Check token (should be pre-validated)
 * @returns Redirect to admin panel
 */
async function createSession(user: User, appCheckToken: string) {
  const idTokenResult = await user.getIdTokenResult()
  writeSession(
    idTokenResult.token,
    appCheckToken,
    !!idTokenResult.claims?.admin
  )
  console.log('Logged in', user.email, {
    isAdmin: !!idTokenResult.claims?.admin,
  })
  return redirect('/admin/users')
}

/**
 * Refreshes the session with new tokens from client
 *
 * Called by client-side when Firebase tokens are refreshed.
 *
 * **Security:**
 * - First validates the EXISTING session tokens (prevents tamper-and-refresh attack)
 * - Detects partial tampering (only one token present) and rejects refresh
 * - Then validates the NEW tokens before updating session
 * - Returns error object if tampering detected (client must handle logout)
 * - This prevents attackers from bypassing App Check by tampering with cookie then refreshing
 *
 * **Tampering Detection:**
 * - Both tokens tampered → validation fails → return error
 * - One token tampered → validation fails → return error
 * - Only one token present in session → partial session → return error
 * - No session + no new tokens → normal "not logged in" state → no error
 *
 * **Important:** Client must check return value and trigger logout on error!
 *
 * @param idToken - Fresh ID token from Firebase Auth client
 * @param appCheckToken - Fresh App Check token from client
 * @returns { error: 'TAMPERED' | 'NO_ID_TOKEN' | 'INVALID' } on error, or void on success
 */
export async function refreshSession(
  idToken: string | undefined,
  appCheckToken: string
): Promise<{ error: string } | void> {
  // SECURITY: Validate EXISTING session tokens first
  // This prevents attack: tamper with cookie → client auto-refreshes with valid tokens
  const existingIdToken = await getIdToken()
  const existingAppCheckToken = await getAppCheckToken()

  // If no idToken from client AND no existing session, this is just "not logged in"
  // Don't return error (would cause logout loop on login page)
  if (!idToken && !existingIdToken && !existingAppCheckToken) {
    // No session, no new tokens - user is simply not logged in
    // This is normal, not an error
    return
  }

  // If no idToken from client BUT there IS an existing session
  // This means session should be cleared
  if (!idToken && (existingIdToken || existingAppCheckToken)) {
    console.log('No idToken from client, clearing existing session')
    await (await cookies()).delete('session')
    return { error: 'NO_ID_TOKEN' }
  }

  // Check for partial tampering (only one token present in existing session)
  const hasOnlyOneToken =
    (existingIdToken && !existingAppCheckToken) ||
    (!existingIdToken && existingAppCheckToken)

  if (hasOnlyOneToken) {
    console.error(
      'Refresh rejected - partial session (only one token present), likely tampered'
    )
    // Session is compromised, delete it and return error
    await (await cookies()).delete('session')
    return { error: 'TAMPERED' }
  }

  if (existingIdToken && existingAppCheckToken) {
    // There's an existing session - validate it before allowing refresh
    const existingValidation = await validateTokens({
      idToken: existingIdToken,
      appCheckToken: existingAppCheckToken,
    })

    if (!existingValidation.valid) {
      console.error(
        'Refresh rejected - existing session has tampered tokens:',
        existingValidation.error
      )
      // Existing session is compromised, delete it and return error
      await (await cookies()).delete('session')
      return { error: 'TAMPERED' }
    }
  }

  // At this point, we know idToken exists (early returns handled above)
  if (!idToken) {
    // This should never happen due to earlier checks, but TypeScript needs it
    console.error('Unexpected: idToken is undefined after validation checks')
    await (await cookies()).delete('session')
    return { error: 'NO_ID_TOKEN' }
  }

  // Validate the NEW tokens from client
  const validation = await validateTokens({ idToken, appCheckToken })
  if (!validation.valid) {
    console.error('Refresh failed - invalid new tokens:', validation.error)
    await (await cookies()).delete('session')
    return { error: 'INVALID' }
  }

  const { currentUser } = await getAppForUser(idToken)

  if (!currentUser) {
    // if there is no session either, go to login
    if (!existingIdToken) return redirect('/admin')
    return
  }
  if (!existingIdToken) {
    console.log()
    // if there was no session, treat this as login
    return createSession(currentUser, appCheckToken)
  }
  const idTokenResult = await currentUser.getIdTokenResult()
  writeSession(idToken, appCheckToken, !!idTokenResult.claims?.admin)
  console.log('Refreshed', currentUser.email, {
    isAdmin: !!idTokenResult.claims?.admin,
  })
}

/**
 * Logs out the user by clearing the session cookie
 *
 * **Logout Flow:**
 * 1. Delete session cookie (clears server-side state)
 * 2. Redirect to /admin login page
 *
 * **Important: Client-side logout preferred**
 * This function should primarily be called from client-initiated logout actions,
 * not from server-side validation failures. For invalid tokens:
 * - Server actions return error objects
 * - Client components call useUserSession().logout() hook
 * - Client hook calls this function AND Firebase client signOut()
 *
 * This approach ensures proper cleanup of both server session and Firebase client state.
 *
 * @returns Redirect to /admin login page
 */
export async function logout() {
  console.log('Logout')
  await (await cookies()).delete('session')
  return redirect('/admin')
}

export async function getIdToken(): Promise<string | undefined> {
  const cookie = (await cookies()).get('session')
  // Use || instead of ?? to handle empty strings (after cookie deletion)
  const session = JSON.parse(cookie?.value || '{}')
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

/**
 * Redirects already-authenticated users from login page to admin panel
 *
 * This function provides a UX optimization for the /admin login page.
 * It checks if the user already has valid authentication tokens and redirects
 * them to /admin/users to skip the login form.
 *
 * **Why validate tokens here?**
 * - Fail-fast behavior: Detect invalid tokens immediately
 * - Better UX: Don't redirect users with expired tokens (they'd just get kicked back)
 * - Consistency: Use the same validation as other server actions
 *
 * **Usage:**
 * Called from /admin/page.tsx before rendering the Login component.
 *
 * @returns Redirect to /admin/users if tokens are valid, undefined otherwise
 */
export async function optimisticCheckLogin() {
  // Get tokens from session
  const tokens = {
    idToken: await getIdToken(),
    appCheckToken: await getAppCheckToken(),
  }

  // If no tokens present, user needs to login
  if (!tokens.idToken || !tokens.appCheckToken) {
    return
  }

  // Validate tokens before redirecting
  // This ensures we don't redirect users with expired/invalid tokens
  const validation = await validateTokens(tokens)
  if (!validation.valid) {
    // Tokens are invalid, let user see login page
    return
  }

  // Tokens are valid, redirect to admin panel
  return redirect('/admin/users')
}
