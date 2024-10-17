'use client'
import { ReactNode } from 'react'

import { UserProvider } from 'src/lib/firebase/UserProvider'

export default function AdminLayout({ children }: { children: ReactNode }) {
  return <UserProvider>{children}</UserProvider>
}
