'use client'
import { useUserSession } from 'src/lib/firebase/auth'
import { useEffect } from 'react'

export default function ClientLogout() {
  const { logout } = useUserSession()
  useEffect(() => {
    logout()
  }, [logout])

  return <></>
}
