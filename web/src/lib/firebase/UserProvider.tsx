import { createContext, ReactNode, useState } from 'react'

import { User } from 'firebase/auth'

import { useFirebaseAuth } from './auth'

interface UserState {
  initialized: boolean
  firebaseUser?: User
  setFirebaseUser: (user: User | undefined) => void
  firebaseToken?: string
  setFirebaseToken: (token: string | undefined) => void
}

export const UserContext = createContext<UserState>({
  initialized: false,
  setFirebaseUser: () => {},
  setFirebaseToken: () => {},
})

export const UserProvider = ({ children }: { children: ReactNode }) => {
  const [initialized, setInitialized] = useState(false)
  const [firebaseUser, setFirebaseUser] = useState<User>()
  const [firebaseToken, setFirebaseToken] = useState<string>()

  useFirebaseAuth(setFirebaseUser, setFirebaseToken, () => setInitialized(true))

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
