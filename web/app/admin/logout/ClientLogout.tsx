'use client'
import { useEffect } from 'react'

import { useUserSession } from 'src/lib/firebase/auth'

export default function ClientLogout() {
  const { logout } = useUserSession()
  useEffect(() => {
    logout()
  }, [logout])

  return <></>
}
