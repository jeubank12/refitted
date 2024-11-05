'use client'

import styles from 'styles/Home.module.css'
import { useUserSession } from 'src/lib/firebase/auth'

export default function Logout() {
  const { logout, firebaseUser } = useUserSession()

  return (
    firebaseUser && (
      <div className={styles.header}>
        <span>
          Logged in as {`${firebaseUser?.displayName} (${firebaseUser?.email})`}
        </span>
        <button onClick={logout}>Logout</button>
      </div>
    )
  )
}
