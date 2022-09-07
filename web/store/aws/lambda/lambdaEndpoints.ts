import { BaseQueryFn } from '@reduxjs/toolkit/query/react'

import { InvokeCommand } from '@aws-sdk/client-lambda'

import { ReduxState } from 'store'
import { getLambdaClient } from './lambdaSelectors'
import { AwsEndpointBuilder } from '../types'

const invokeLambda: (functionName: string) => BaseQueryFn<
  void,
  number | undefined,
  string,
  // eslint-disable-next-line @typescript-eslint/ban-types
  {},
  // eslint-disable-next-line @typescript-eslint/ban-types
  {}
> =
  (functionName: string) =>
  (_, { getState }) => {
    const client = getLambdaClient(getState() as ReduxState)
    if (client) {
      const command = new InvokeCommand({ FunctionName: functionName })
      return client.send(command).then(result => ({ data: result.StatusCode }))
    }
    return { error: 'Not logged in' }
  }

const lambdaEndpoints = (builder: AwsEndpointBuilder) => ({
  test: builder.query<number | undefined, void>({
    queryFn: invokeLambda('SetFirebaseclaims'),
  }),
})

export default lambdaEndpoints
