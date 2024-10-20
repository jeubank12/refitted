'use client'
import { ReactNode } from 'react'

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

      <main className={styles.main}>{children}</main>
    </div>
  )
}
