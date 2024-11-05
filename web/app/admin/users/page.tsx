import { listAllUsers } from 'src/lib/aws/actions/lambda'
import ListUsersTable from './ListUsersTable'

export default async function UsersList() {
  const data = await listAllUsers().then(result => result.data)

  if (!data?.users.length) return <div>empty</div>
  else return <ListUsersTable users={data.users} />
}
