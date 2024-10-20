import { useState, useEffect } from 'react'

import { getToken } from 'firebase/app-check'

import { useAppCheck } from './firebaseApp'

export const useAppCheckToken = () => {
  const appCheck = useAppCheck()

  const appCheckDefined = !!appCheck.current

  const [callback, setCallback] = useState<() => Promise<string>>()
  useEffect(() => {
    const check = appCheck.current
    if (check && !callback)
      setCallback(
        () => () =>
          getToken(check, /* forceRefresh */ false).then(
            success => success.token
          )
      )
  }, [appCheckDefined])

  return { getAppCheckToken: callback }
}
