import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux'

import { ReduxDispatch, ReduxState } from '.'

export const useReduxDispatch = () => useDispatch<ReduxDispatch>()
export const useReduxSelector: TypedUseSelectorHook<ReduxState> = useSelector
