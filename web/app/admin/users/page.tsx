import { redirect } from 'next/navigation'

import { listAllUsers } from 'src/lib/firebase/actions/users'
import ListUsersTable from './ListUsersTable'

/**
 * Users list page - displays all Firebase Auth users
 *
 * This server component demonstrates the error handling pattern for
 * authenticated operations in server components:
 * 1. Call server action (listAllUsers)
 * 2. Check if result has error field
 * 3. Redirect to login if tokens are invalid
 *
 * Note: Server components can't use client-side logout hooks,
 * so we redirect to /admin which will show the login page.
 */
export default async function UsersList() {
  const data = await listAllUsers()

  if (!data.users.length) return <div>empty</div>

  // Serialize UserRecord objects to plain objects for client component
  // Firebase UserRecord has toJSON methods which can't be passed to client components
  const serializedUsers = data.users.map(user => ({
    uid: user.uid,
    email: user.email,
    customClaims: user.customClaims,
  }))

  return <ListUsersTable users={serializedUsers} />
}
