import {
  initializeApp,
  cert,
  ServiceAccount,
  getApps,
} from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'

import serviceAccount from '../firebase.json' assert { type: 'json' }

export async function listAllUsers() {
  // TODO add app check
  if (!getApps().length) {
    initializeApp({
      credential: cert(serviceAccount as ServiceAccount),
      databaseURL: 'https://refitted-361ee.firebaseio.com',
    })
  }
  const users = await getAuth().listUsers(1000)

  return users
}
