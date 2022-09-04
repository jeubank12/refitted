import { AnyAction, configureStore, ThunkAction } from '@reduxjs/toolkit'
import { createWrapper } from 'next-redux-wrapper'
import authSlice from './auth/authSlice'

  const makeStore = () => configureStore({
    reducer: {
      auth: authSlice,
    },
    middleware: getDefaultMiddleware =>
      getDefaultMiddleware({
        thunk: true,
        immutableCheck: true,
        serializableCheck: false,
      }),
    devTools: !!process.env.NEXT_PUBLIC_DEV_TOOLS_ENABLED,
  })

export type ReduxStore = ReturnType<typeof makeStore>

export type ReduxState = ReturnType<ReduxStore['getState']>

export type ReduxThunk<ReturnType = void> = ThunkAction<
  ReturnType,
  ReduxState,
  unknown,
  AnyAction
>

export type ReduxDispatch = ReduxStore['dispatch']

export default createWrapper(makeStore)
