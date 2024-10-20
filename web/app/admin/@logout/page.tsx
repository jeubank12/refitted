'use client'
import styles from 'styles/Home.module.css'
import {
  useFirebaseAuth,
  useFirebaseUser,
  useLogout,
} from 'src/lib/firebase/auth'

export default function Logout() {
  useFirebaseAuth()
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
