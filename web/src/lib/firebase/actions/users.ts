'use server'
import { redirect } from 'next/navigation'

import {
  initializeApp,
  cert,
  ServiceAccount,
  getApps,
} from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'
import { getAppCheck } from 'firebase-admin/app-check'

import { getIdToken } from './auth'
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
      return redirect('/admin')
    }
  } else {
    console.error('No app check token present')
    return redirect('/admin')
  }

  const idToken = await getIdToken()
  if (idToken) {
    try {
      await getAuth().verifyIdToken(idToken)
      console.debug('Id token verified')
    } catch (error) {
      console.error('Failed to verify ID token', error)
      return redirect('/admin')
    }
  } else {
    console.error('No id token present')
    return redirect('/admin')
  }

  const users = await getAuth().listUsers(1000)

  return users
}
