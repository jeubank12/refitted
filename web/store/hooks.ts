import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux'
import { ReduxDispatch, ReduxState } from './store'

export const useReduxDispatch = () => useDispatch<ReduxDispatch>()
export const useReduxSelector: TypedUseSelectorHook<ReduxState> = useSelector
