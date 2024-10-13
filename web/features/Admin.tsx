import { useContext } from 'react'

import Head from 'next/head'

import styles from 'styles/Home.module.css'
import Login from 'features/auth/login'
import Logout from 'features/auth/logout'
import UserList from 'features/users/UserList'
import { UserContext } from 'src/lib/firebase/UserProvider'

const AdminContent = () => {
  const { firebaseUser, initialized } = useContext(UserContext)
  const isLoggedIn = !!firebaseUser
  const initializing = !initialized
  return (
    <div className={styles.container}>
      <Head>
        <title>Litus Animae</title>
        <meta name="description" content="site for Litus Animae" />
      </Head>

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
