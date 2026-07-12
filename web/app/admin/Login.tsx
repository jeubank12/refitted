'use client'

import { useLogin } from 'src/lib/firebase/auth'

export default function Login() {
  const { error, doLogin } = useLogin()

  return (
    <>
      <button onClick={doLogin}>Sign In with Google</button>
      {error && <p role="alert">{error}</p>}
    </>
  )
}
