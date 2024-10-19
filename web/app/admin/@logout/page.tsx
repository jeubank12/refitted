'use client'

import styles from 'styles/Home.module.css'
import { useFirebaseUser, useLogout } from 'src/lib/firebase/auth'

const Logout = () => {
  const { doLogout } = useLogout()
  const firebaseUser = useFirebaseUser()

  return (
    firebaseUser && (
      <div className={styles.header}>
        <span>
          Logged in as {`${firebaseUser?.displayName} (${firebaseUser?.email})`}
        </span>
        <button onClick={doLogout}>Logout</button>
      </div>
    )
  )
}

export default Logout
