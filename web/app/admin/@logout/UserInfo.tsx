'use client'
import Button from '@mui/material/Button'

import styles from 'styles/Home.module.css'
import { useUserSession } from 'src/lib/firebase/auth'

export default function UserInfo({
  name,
  email,
}: {
  name?: string
  email?: string
}) {
  const { logout } = useUserSession()

  return (
    <div className={styles.header}>
      <span>
        Logged in as {`${name} (${email})`}
      </span>
      {/** add form state to disable after submit? */}
      <Button onClick={logout} variant="contained" color="error">
        Logout
      </Button>
    </div>
  )
}
