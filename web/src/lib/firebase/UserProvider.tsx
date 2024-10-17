import { createContext, ReactNode, useState } from 'react'

import { User } from 'firebase/auth'

import { useFirebaseAuth } from './auth'

interface UserState {
  initialized: boolean
  firebaseUser?: User | null
  setFirebaseUser: (user: User | null) => void
  firebaseToken?: string
  setFirebaseToken: (token: string | undefined) => void
}

export const UserContext = createContext<UserState>({
  initialized: false,
  setFirebaseUser: () => {},
  setFirebaseToken: () => {},
})

export const UserProvider = ({ children }: { children: ReactNode }) => {
  const [firebaseUser, setFirebaseUser] = useState<User | null>()
  const [firebaseToken, setFirebaseToken] = useState<string>()

  useFirebaseAuth(setFirebaseUser, setFirebaseToken)

  const initialized = firebaseUser !== undefined

  return (
    <UserContext.Provider
      value={{
        initialized,
        firebaseUser,
        setFirebaseUser,
        firebaseToken,
        setFirebaseToken,
      }}
    >
      {children}
    </UserContext.Provider>
  )
}
