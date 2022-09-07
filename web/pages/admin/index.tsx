import { useEffect } from 'react'

import type { NextPage } from 'next'
import Head from 'next/head'

import styles from 'styles/Home.module.css'
import Login from 'features/auth/login'
import Logout from 'features/auth/logout'
import { getIsInitializing, getIsLoggedIn } from 'store/auth/authSelectors'
import { initializeFirebase } from 'store/auth/authSlice'
import { useReduxDispatch, useReduxSelector } from 'store/hooks'
import store from 'store'
import UserList from 'features/users/UserList'

const Admin: NextPage = () => {
  const dispatch = useReduxDispatch()
  useEffect(() => {
    dispatch(initializeFirebase)
  }, [])
  const isLoggedIn = useReduxSelector(getIsLoggedIn)
  const initializing = useReduxSelector(getIsInitializing)
  return (
    <div className={styles.container}>
      <Head>
        <title>Litus Animae</title>
        <meta name="description" content="site for Litus Animae" />
      </Head>

      <header>{isLoggedIn && <Logout />}</header>

      <main className={styles.main}>
        { /* TODO initializing/loading */ }
        {!isLoggedIn && !initializing && <Login />}
        {isLoggedIn && <UserList />}
      </main>
    </div>
  )
}

export default store.withRedux(Admin)
