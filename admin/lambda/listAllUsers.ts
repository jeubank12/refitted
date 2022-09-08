import { initializeApp, cert, ServiceAccount } from 'firebase-admin/app'
import { getAuth, ListUsersResult } from 'firebase-admin/auth'

import serviceAccount from '../firebase.json' assert { type: 'json' }

initializeApp({
  // WARNING: I'm a hack!!
  credential: cert(serviceAccount as ServiceAccount),
  databaseURL: 'https://refitted-361ee.firebaseio.com',
})

interface ListUsersEvent {
  nextPageToken?: string
}

const listAllUsers = async ({
  nextPageToken,
}: ListUsersEvent): Promise<ListUsersResult> => {

  // List batch of users, 1000 at a time.
  const users = await getAuth().listUsers(1000, nextPageToken)

  return users
  // we want to pass the error to the caller
  // .catch((error) => {
  //   console.log("Error listing users:", error);
  // });
}

export const handler = listAllUsers
