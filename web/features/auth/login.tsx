import { useLogin } from 'src/lib/firebase/auth'

const Login = () => {
  const { doLogin } = useLogin()
  return <button onClick={doLogin}>Sign In with Google</button>
}

export default Login
