import type { NextPage } from 'next'
import Head from 'next/head'
import { useEffect } from 'react'
import { Provider } from 'react-redux'
import { getIsLoggedIn } from '../../store/auth/authSelectors'
import { initializeFirebase } from '../../store/auth/authSlice'
import { useReduxDispatch, useReduxSelector } from '../../store/hooks'
import store from '../../store/store'
import Login from '../../features/auth/login'
import Logout from '../../features/auth/logout'
import styles from '/styles/Home.module.css'

const AdminContent = () => {
  const dispatch = useReduxDispatch()
  useEffect(() => {
    dispatch(initializeFirebase)
  }, [])
  const isLoggedIn = useReduxSelector(getIsLoggedIn)
  return isLoggedIn ? <Logout /> : <Login />
}

const Admin: NextPage = () => {
  return (
    <Provider store={store}>
      <div className={styles.container}>
        <Head>
          <title>Litus Animae</title>
          <meta name="description" content="site for Litus Animae" />
        </Head>

        <main className={styles.main}>
          <AdminContent />
        </main>
      </div>
    </Provider>
  )
}

export default Admin
