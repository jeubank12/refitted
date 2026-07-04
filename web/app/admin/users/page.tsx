import { listAllUsers } from 'src/lib/firebase/actions/users'
import { getGroupsConfig } from 'src/lib/aws/groups'
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

  // REFITTED_GROUPS_B64 may be unset in some environments (e.g. local dev) -
  // degrade to "no groups" rather than crashing the whole page.
  let groups: { name: string; id: string }[] = []
  try {
    groups = Object.entries(getGroupsConfig()).map(([name, config]) => ({
      name,
      id: config.id,
    }))
  } catch (error) {
    console.warn('Groups config unavailable, group assignment disabled', error)
  }
  const groupNamesById = Object.fromEntries(groups.map(g => [g.id, g.name]))

  // Serialize UserRecord objects to plain objects for client component
  // Firebase UserRecord has toJSON methods which can't be passed to client components
  const serializedUsers = data.users.map(user => ({
    uid: user.uid,
    email: user.email,
    emailVerified: user.emailVerified,
    customClaims: user.customClaims,
    createdAt: user.metadata.creationTime
      ? new Date(user.metadata.creationTime).getTime()
      : null,
    lastSignInAt: user.metadata.lastSignInTime
      ? new Date(user.metadata.lastSignInTime).getTime()
      : null,
  }))

  return (
    <>
      <ListUsersTable
        users={serializedUsers}
        groups={groups}
        groupNamesById={groupNamesById}
      />
    </>
  )
}
