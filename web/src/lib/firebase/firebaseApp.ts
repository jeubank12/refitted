import { initializeApp } from 'firebase/app'
import { getAnalytics } from 'firebase/analytics'
import { AppCheck, initializeAppCheck } from 'firebase/app-check'
import { firebaseConfig, recaptchaProvider } from './firebaseConfig'
import { useEffect } from 'react'

declare global {
  var FIREBASE_APPCHECK_DEBUG_TOKEN: boolean
}

// Initialize Firebase
export const app = initializeApp(firebaseConfig)

globalThis.FIREBASE_APPCHECK_DEBUG_TOKEN =
  process.env.NEXT_PUBLIC_DEV_TOOLS_ENABLED === 'true'

let appCheck: AppCheck | null = null
export const useAppCheck = () => {
  useEffect(() => {
    if (!appCheck) {
      appCheck = initializeAppCheck(app, {
        provider: recaptchaProvider,
      })
    }
  }, [])
  return appCheck
}
