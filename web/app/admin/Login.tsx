'use client'

import { useLogin } from 'src/lib/firebase/auth'

export default function Login() {
  const { doLogin } = useLogin()

  return <button onClick={doLogin}>Sign In with Google</button>
}
