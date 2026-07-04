import { listAllUsers } from 'src/lib/firebase/actions/users'
import ListUsersTable from './ListUsersTable'

/**
 * Users list page - displays all Firebase Auth users
 *
 * listAllUsers() rethrows on failure (after revoking the session on auth
 * errors), which surfaces Next's nearest error boundary rather than
 * redirecting from here.
 */
export default async function UsersList() {
  const data = await listAllUsers()

  if (!data?.users.length) return <div>empty</div>

  // Serialize UserRecord objects to plain objects for client component
  // Firebase UserRecord has toJSON methods which can't be passed to client components
  const serializedUsers = data.users.map(user => ({
    uid: user.uid,
    email: user.email,
    customClaims: user.customClaims,
  }))

  return (
    <>
      <ListUsersTable users={serializedUsers} />
    </>
  )
}
