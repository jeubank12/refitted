import { getSessionUser } from 'src/lib/firebase/actions/auth'
import AdminNavLinks from '../AdminNavLinks'

// Only show nav once there's a session - keeps the login screen bare, same
// gating getSessionUser() already does for the @logout slot's UserInfo.
export default async function Nav() {
  const user = await getSessionUser()
  if (!user) return null
  return <AdminNavLinks />
}
