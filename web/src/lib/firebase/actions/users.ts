'use server'
import {
  initializeApp,
  cert,
  ServiceAccount,
  getApps,
} from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'
import { getAppCheck } from 'firebase-admin/app-check'

import serviceAccount from '../firebase.json' assert { type: 'json' }
import { getAppCheckToken } from './appCheck'

export async function listAllUsers() {
  if (!getApps().length) {
    initializeApp({
      credential: cert(serviceAccount as ServiceAccount),
      databaseURL: 'https://refitted-361ee.firebaseio.com',
    })
  }

  const appCheckToken = await getAppCheckToken()

  if (appCheckToken) {
    try {
      await getAppCheck().verifyToken(appCheckToken)
      console.debug('App check token verified')
    } catch (error) {
      console.error('Failed to verify appcheck token', error)
    }
  } else {
    console.error('No app check token present')
  }
  // TODO verify id token
  const users = await getAuth().listUsers(1000)

  return users
}
