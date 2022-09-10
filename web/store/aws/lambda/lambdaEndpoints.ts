import { BaseQueryFn } from '@reduxjs/toolkit/query/react'

import { InvokeCommand } from '@aws-sdk/client-lambda'
import { ListUsersResult } from 'firebase-admin/auth'
import { toUtf8 } from '@aws-sdk/util-utf8-node'

import { ReduxState } from 'store'
import { getLambdaClient } from '../awsSelectors'
import { awsApi, QueryReturnValue } from '..'

const invokeLambda: <T, M>(
  functionName: string,
  transform: (output: unknown) => QueryReturnValue<T, unknown, M>,
  // if we define an input type, then it can go here
  defaultIfEmpty: () => QueryReturnValue<T, unknown, M>
) => BaseQueryFn<
  void,
  T | undefined,
  unknown,
  // eslint-disable-next-line @typescript-eslint/ban-types
  {},
  M
> =
  (functionName, transform, defaultIfEmpty) =>
  (_, { getState }) => {
    const client = getLambdaClient(getState() as ReduxState)
    if (client) {
      const command = new InvokeCommand({ FunctionName: functionName })
      return client
        .send(command)
        .then(
          result =>
            result.Payload
              ? transform(JSON.parse(toUtf8(result.Payload)))
              : defaultIfEmpty(),
          error => ({ error })
        )
        .catch(error => ({ error: ['error deserializing the result', error] }))
    }
    return Promise.resolve({ error: 'Not logged in' })
  }

export const lambdaExtendedApi = awsApi.injectEndpoints({
  endpoints: builder => ({
    getUsers: builder.query<ListUsersResult, void>({
      // eslint-disable-next-line @typescript-eslint/ban-types
      queryFn: invokeLambda<ListUsersResult, {}>(
        'RefittedListFirebaseUsers',
        output => {
          if (typeof output === 'object' && output && 'users' in output)
            return { data: output as ListUsersResult }
          else return { error: 'unknown result format' }
        },
        () => ({ error: 'unhandled empty result' })
      ),
    }),
  }),
  // to allow fast reload
  overrideExisting: true,
})

export const { useGetUsersQuery } = lambdaExtendedApi
