import { doLogin } from '../../store/auth/authSlice'
import { useReduxDispatch } from '../../store/hooks'

const Login = () => {
  const dispatch = useReduxDispatch()
  return <button onClick={() => dispatch(doLogin)}>Sign In with Google</button>
}

export default Login
