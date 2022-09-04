import type { NextPage } from 'next'
import Head from 'next/head'
import { useEffect } from 'react'
import { getIsLoggedIn } from '../../store/auth/authSelectors'
import { initializeFirebase } from '../../store/auth/authSlice'
import { useReduxDispatch, useReduxSelector } from '../../store/hooks'
import store from '../../store/store'
import Login from '../../features/auth/login'
import Logout from '../../features/auth/logout'
import styles from '/styles/Home.module.css'

const Admin: NextPage = () => {
  const dispatch = useReduxDispatch()
  useEffect(() => {
    dispatch(initializeFirebase)
  }, [])
  const isLoggedIn = useReduxSelector(getIsLoggedIn)
  return (
      <div className={styles.container}>
        <Head>
          <title>Litus Animae</title>
          <meta name="description" content="site for Litus Animae" />
        </Head>

        <main className={styles.main}>
          {isLoggedIn ? <Logout /> : <Login />}
        </main>
      </div>
  )
}

export default store.withRedux(Admin)
