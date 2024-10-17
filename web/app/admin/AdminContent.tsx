'use client'
import { useContext } from 'react'

import styles from 'styles/Home.module.css'
import Login from 'app/admin/Login'
import Logout from 'app/admin/Logout'
import UserList from 'app/admin/UserList'
import { UserContext } from 'src/lib/firebase/UserProvider'

const AdminContent = () => {
  const { firebaseUser, initialized } = useContext(UserContext)
  const isLoggedIn = !!firebaseUser
  const initializing = !initialized
  return (
    <div className={styles.container}>
      <header>{isLoggedIn && <Logout />}</header>

      <main className={styles.main}>
        {/* TODO initializing/loading */}
        {!isLoggedIn && !initializing && <Login />}
        {isLoggedIn && <UserList />}
      </main>
    </div>
  )
}

export default AdminContent
