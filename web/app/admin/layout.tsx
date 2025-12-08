'use client'
import { ReactNode } from 'react'
import { AppRouterCacheProvider } from '@mui/material-nextjs/v15-appRouter'

import styles from 'styles/Home.module.css'

export default function AdminLayout({
  children,
  logout,
}: {
  children: ReactNode
  logout: ReactNode
}) {
  return (
    <AppRouterCacheProvider>
      <div className={styles.container}>
        <header>{logout}</header>

        <main className={styles.main}>{children}</main>
      </div>
    </AppRouterCacheProvider>
  )
}
