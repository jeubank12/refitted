import type { NextPage } from 'next'

import AdminContent from 'features/Admin'
import { UserProvider } from 'src/lib/firebase/UserProvider'

const Admin: NextPage = () => {
  return (
    <UserProvider>
      <AdminContent />
    </UserProvider>
  )
}

export default Admin
