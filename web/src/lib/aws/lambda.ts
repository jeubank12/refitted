import { useCallback, useEffect, useState } from 'react'

// import { useAppCheck } from '../firebase/firebaseApp'
import { InvokeCommand } from '@aws-sdk/client-lambda'
import { ListUsersResult } from 'firebase-admin/auth'
import { toUtf8, fromUtf8 } from '@aws-sdk/util-utf8-node'

import { QueryReturnValue } from '.'

import { useLambdaClient } from './awsClient'
import { useAppCheckToken } from '../firebase/auth'

function useLambda<T, M>(
  functionName: string,
  transform: (output: unknown) => QueryReturnValue<T, unknown, M>,
  // if we define an input type, then it can go here
  defaultIfEmpty: () => QueryReturnValue<T, unknown, M>
) {
  const [result, setResult] = useState<QueryReturnValue<T, unknown, M>>()
  const [error, setError] = useState<unknown>()
  const [isLoading, setIsLoading] = useState(false)
  const [isInitialized, setIsInitialized] = useState(false)
  const client = useLambdaClient()
  const { getAppCheckToken } = useAppCheckToken()

  useEffect(() => {
    setIsInitialized(!!client)
  }, [client])

  const invokeLambda = useCallback(() => {
    if (client) {
      setIsLoading(true)
      return getAppCheckToken()
        .then(appCheckToken => {
          const command = new InvokeCommand({
            FunctionName: functionName,
            Payload: fromUtf8(
              JSON.stringify({ firebaseAppCheckToken: appCheckToken })
            ),
          })
          return client
            .send(command)
            .then(
              result =>
                setResult(
                  result.Payload
                    ? transform(JSON.parse(toUtf8(result.Payload)))
                    : defaultIfEmpty()
                ),
              error => setError(error)
            )
            .catch(error =>
              setError({ error: ['error deserializing the result', error] })
            )
        })
        .catch(error => setError({ error: ['error in AppCheck', error] }))
        .finally(() => setIsLoading(false))
    }
  }, [client, setIsLoading, getAppCheckToken, setError, setResult])

  return { invokeLambda, result, error, isLoading, isInitialized }
}

export const useGetUsersQuery = () => {
  const { invokeLambda, result, error, isLoading, isInitialized } = useLambda<
    ListUsersResult,
    void
  >(
    'RefittedListFirebaseUsers',
    output => {
      if (typeof output === 'object' && output && 'users' in output)
        return { data: output as ListUsersResult }
      else return { error: 'unknown result format' }
    },
    () => ({ error: 'unhandled empty result' })
  )

  useEffect(() => {
    invokeLambda()
  }, [isInitialized])

  return { data: result?.data, error, isLoading }
}
