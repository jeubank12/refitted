'use client'
import styles from 'styles/Home.module.css'
import { useUserSession } from 'src/lib/firebase/auth'

export default function UserInfo({
  displayName,
  email,
}: {
  displayName?: string
  email?: string
}) {
  // somehow if we are on login page, detect and redirect on successful refresh
  const { logout, firebaseUser } = useUserSession()

  return (
    (firebaseUser || displayName || email) && (
      <div className={styles.header}>
        <span>
          Logged in as{' '}
          {`${firebaseUser?.displayName ?? displayName} (${firebaseUser?.email ?? email})`}
        </span>
        {/** add form state to disable after submit? */}
        <button onClick={logout}>Logout</button>
      </div>
    )
  )
}
