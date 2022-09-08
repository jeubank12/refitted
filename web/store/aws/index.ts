import { BaseQueryFn, createApi } from '@reduxjs/toolkit/dist/query/react'

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
