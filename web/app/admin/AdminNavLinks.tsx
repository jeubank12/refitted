'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

import Tabs from '@mui/material/Tabs'
import Tab from '@mui/material/Tab'

const NAV_ITEMS = [
  { label: 'Users', href: '/admin/users' },
  { label: 'Plans', href: '/admin/workouts' },
]

export default function AdminNavLinks() {
  const pathname = usePathname()
  const activeIndex = NAV_ITEMS.findIndex(item => pathname?.startsWith(item.href))

  return (
    <Tabs
      value={activeIndex === -1 ? false : activeIndex}
      textColor="primary"
      indicatorColor="primary"
    >
      {NAV_ITEMS.map(item => (
        <Tab key={item.href} label={item.label} component={Link} href={item.href} />
      ))}
    </Tabs>
  )
}
