import { createContext, ReactNode, useState } from 'react'

import { User } from 'firebase/auth'

import { useFirebaseAuth } from './auth'

interface UserState {
  initialized: boolean
  firebaseUser?: User | null
  setFirebaseUser: (user: User | null) => void
}

export const UserContext = createContext<UserState>({
  initialized: false,
  setFirebaseUser: () => {},
})

export const UserProvider = ({ children }: { children: ReactNode }) => {
  const [firebaseUser, setFirebaseUser] = useState<User | null>()

  useFirebaseAuth(setFirebaseUser)

  const initialized = firebaseUser !== undefined

  return (
    <UserContext.Provider
      value={{
        initialized,
        firebaseUser,
        setFirebaseUser,
      }}
    >
      {children}
    </UserContext.Provider>
  )
}
