import { useSelector } from 'react-redux'

import styles from 'styles/Home.module.css'
import { getUserInfo } from 'store/auth/authSelectors'
import { doLogout } from 'store/auth/authSlice'
import { useReduxDispatch } from 'store/hooks'

const Logout = () => {
  const dispatch = useReduxDispatch()
  const { name, email } = useSelector(getUserInfo)
  return (
    <div className={styles.header}>
      <span>Logged in as {`${name} (${email})`}</span>
      <button onClick={() => dispatch(doLogout)}>Logout</button>
    </div>
  )
}

export default Logout
