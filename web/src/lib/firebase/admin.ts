import 'server-only'
import { getAuth } from 'firebase-admin/auth'
import { getAppCheck } from 'firebase-admin/app-check'
import { cert, getApps, initializeApp, ServiceAccount } from 'firebase-admin/app'

function ensureInitialized() {
  if (!getApps().length) {
    const b64 = process.env.FIREBASE_SERVICE_ACCOUNT_B64
    if (!b64) throw new Error('FIREBASE_SERVICE_ACCOUNT_B64 is not set')
    const serviceAccount = JSON.parse(
      Buffer.from(b64, 'base64').toString('utf8')
    ) as ServiceAccount
    initializeApp({
      credential: cert(serviceAccount),
      databaseURL: 'https://refitted-361ee.firebaseio.com',
    })
  }
}

export function adminAuth() {
  ensureInitialized()
  return getAuth()
}

export function adminAppCheck() {
  ensureInitialized()
  return getAppCheck()
}
