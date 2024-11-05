import { listAllUsers } from 'src/lib/firebase/actions/users'
import ListUsersTable from './ListUsersTable'

export default async function UsersList() {
  const data = await listAllUsers()

  if (!data?.users.length) return <div>empty</div>
  else return <ListUsersTable users={data.users} />
}
