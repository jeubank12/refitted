import { listAllUsers } from 'src/lib/aws/actions/lambda'
import ListUsersTable from './ListUsersTable'

export default async function UsersList() {
  const data = await listAllUsers()

  if (!data?.users.length) return <div>empty</div>
  else return <ListUsersTable users={data.users} />
}
