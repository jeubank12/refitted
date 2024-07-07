import { BaseQueryFn, createApi } from '@reduxjs/toolkit/query/react'

/** copied from @reduxjs baseQueryTypes.d.ts */
export type QueryReturnValue<T, E, M> = {
 data: T,
 error?: undefined,
 meta?: M
} | {
  data?: undefined,
  error: E,
  meta?: M
}

const defaultBaseQuery: BaseQueryFn = () => ({
  error: 'queryFn not provided in endpoint',
})

export const awsApi = createApi({
  reducerPath: 'aws',
  baseQuery: defaultBaseQuery,
  endpoints: () => ({}),
})

const awsReducer = { [awsApi.reducerPath]: awsApi.reducer }

export default awsReducer
