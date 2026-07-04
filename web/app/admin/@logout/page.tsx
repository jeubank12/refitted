import { getSessionUser } from 'src/lib/firebase/actions/auth'
import UserInfo from './UserInfo'

export default async function Logout() {
  const user = await getSessionUser()
  if (!user) return null
  return <UserInfo name={user.name} email={user.email} />
}
