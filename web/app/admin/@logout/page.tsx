import { getUserInfo } from 'src/lib/firebase/actions/auth'
import UserInfo from './UserInfo'

export default async function Logout() {
  const userInfo = await getUserInfo()

  return <UserInfo {...userInfo} />
}
