'use client'
import Button from '@mui/material/Button'
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
        {/** add form state to disable after submit? */}
        <Button
          onClick={logout}
          variant="contained"
          color="error"
        >
          Logout
        </Button>
      </div>
    )
  )
}
