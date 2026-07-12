import { listAllUsers } from 'src/lib/firebase/actions/users'
import { listAllGroups, listAssignableGroups } from 'src/lib/aws/groups'
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

  // REFITTED_AWS_ACCOUNT_ID/REFITTED_ANDROID_POOL_ID may be unset in some environments
  // (e.g. local dev) - degrade to "no groups" rather than crashing the whole page.
  // `groups` (assignable = paid only) drives the dropdown options; `groupNamesById`
  // (static + paid) resolves display names for any claim value, including Free/Anon
  // which are never assignable but can still be a user's current group.
  let groups: { name: string; id: string }[] = []
  let groupNamesById: Record<string, string> = {}
  try {
    const [assignable, all] = await Promise.all([listAssignableGroups(), listAllGroups()])
    groups = assignable
    groupNamesById = Object.fromEntries(all.map(g => [g.id, g.name]))
  } catch (error) {
    console.warn('Groups config unavailable, group assignment disabled', error)
  }

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
