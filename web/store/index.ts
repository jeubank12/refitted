import { AnyAction, configureStore, ThunkAction } from '@reduxjs/toolkit'
import { createWrapper } from 'next-redux-wrapper'

import authSlice from './auth/authSlice'
import awsReducer, { awsApi } from './aws'

const makeStore = () =>
  configureStore({
    reducer: {
      auth: authSlice,
      ...awsReducer,
    },
    middleware: getDefaultMiddleware =>
      getDefaultMiddleware({
        thunk: true,
        immutableCheck: true,
        serializableCheck: false,
      }).concat(awsApi.middleware),
    devTools: process.env.NEXT_PUBLIC_DEV_TOOLS_ENABLED === 'true',
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

const wrapper = createWrapper(makeStore)

export default wrapper
