'use client'
import { useEffect } from 'react'

import { useRouter } from 'next/navigation'

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
  const router = useRouter()

  useEffect(() => {
    if (firebaseUser === null) {
      router.push('/admin')
    }
  }, [firebaseUser])

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
