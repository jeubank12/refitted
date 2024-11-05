import { optimisticCheckLogin } from 'src/lib/firebase/actions/auth'
import Login from './Login'

export default async function Page() {
  await optimisticCheckLogin()

  return <Login />
}
