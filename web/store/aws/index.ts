import { createApi } from '@reduxjs/toolkit/dist/query/react'

import lambdaEndpoints from './lambda/lambdaEndpoints'
import { AwsBaseQuery, AwsEndpointBuilder } from './types'

const defaultBaseQuery: AwsBaseQuery = () => ({
  error: 'queryFn not provided in endpoint',
})

const awsApi = createApi({
  reducerPath: 'aws',
  baseQuery: defaultBaseQuery,
  endpoints: (builder: AwsEndpointBuilder) => ({
    ...lambdaEndpoints(builder),
  }),
})

export const { useTestQuery } = awsApi

const awsReducer = { [awsApi.reducerPath]: awsApi.reducer }

export default awsReducer
