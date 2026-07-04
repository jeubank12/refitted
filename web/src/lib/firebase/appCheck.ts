import { useCallback } from 'react'

import { getToken } from 'firebase/app-check'

import { useAppCheck } from './firebaseApp'

export const useAppCheckToken = () => {
  const appCheck = useAppCheck()

  const getAppCheckToken = useCallback(async () => {
    const check = appCheck.current
    if (!check) throw new Error('App Check not initialized')
    const result = await getToken(check, /* forceRefresh */ false)
    return result.token
  }, [appCheck])

  return { getAppCheckToken }
}
