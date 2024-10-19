'use client'
import { ReactNode } from 'react'

import { UserProvider } from 'src/lib/firebase/UserProvider'
import styles from 'styles/Home.module.css'

export default function AdminLayout({
  children,
  logout,
}: {
  children: ReactNode
  logout: ReactNode
}) {
  return (
    <div className={styles.container}>
      <header>{logout}</header>

      <UserProvider>{children}</UserProvider>
    </div>
  )
}
