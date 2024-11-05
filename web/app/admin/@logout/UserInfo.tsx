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
  const { logout, firebaseUser } = useUserSession()

  return (
    (firebaseUser || displayName || email) && (
      <div className={styles.header}>
        <span>
          Logged in as{' '}
          {`${firebaseUser?.displayName ?? displayName} (${firebaseUser?.email ?? email})`}
        </span>
        {/** is disabled better, or something to show loading progress? */}
        <button onClick={logout} disabled={firebaseUser === undefined}>
          Logout
        </button>
      </div>
    )
  )
}
