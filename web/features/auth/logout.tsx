import { useContext } from 'react'

import styles from 'styles/Home.module.css'
import { useLogout, UserContext } from 'src/lib/firebase/auth'

const Logout = () => {
  const { doLogout } = useLogout()
  const { firebaseUser } = useContext(UserContext)
  return (
    <div className={styles.header}>
      <span>
        Logged in as {`${firebaseUser?.displayName} (${firebaseUser?.email})`}
      </span>
      <button onClick={() => doLogout}>Logout</button>
    </div>
  )
}

export default Logout
