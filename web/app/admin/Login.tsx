'use client'

import { useLogin } from 'src/lib/firebase/auth'
import GoogleSignInButton from './GoogleSignInButton'

export default function Login() {
  const { error, doLogin } = useLogin()

  return (
    <>
      <GoogleSignInButton onClick={doLogin} />
      {error && <p role="alert">{error}</p>}
    </>
  )
}
