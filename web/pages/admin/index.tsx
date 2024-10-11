import type { NextPage } from 'next'
import Head from 'next/head'

import styles from 'styles/Home.module.css'
import Login from 'features/auth/login'
import Logout from 'features/auth/logout'
import UserList from 'features/users/UserList'
import { UserContext, useUserContext } from 'src/lib/firebase/auth'

const Admin: NextPage = () => {
  const userContext = useUserContext()
  const { initialized, firebaseUser } = userContext
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
        <UserContext.Provider value={userContext}>
          {/* TODO initializing/loading */}
          {!isLoggedIn && !initializing && <Login />}
          {isLoggedIn && <UserList />}
        </UserContext.Provider>
      </main>
    </div>
  )
}

export default Admin
