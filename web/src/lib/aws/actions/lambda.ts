import { redirect } from 'next/navigation'

import { InvokeCommand } from '@aws-sdk/client-lambda'
import { ListUsersResult } from 'firebase-admin/auth'
import { toUtf8, fromUtf8 } from '@aws-sdk/util-utf8-node'

import { QueryReturnValue } from '..'
import { getClient } from './client'

async function callLambda<T, M>(
  functionName: string,
  transform: (output: unknown) => QueryReturnValue<T, unknown, M>,
  // if we define an input type, then it can go here
  defaultIfEmpty: () => QueryReturnValue<T, unknown, M>
) {
  const command = new InvokeCommand({
    FunctionName: functionName,
    Payload: fromUtf8(JSON.stringify({})),
  })
  const client = getClient()
  if (!client) return redirect('/admin')
  const result = await client.send(command)
  return result.Payload
    ? transform(JSON.parse(toUtf8(result.Payload)))
    : defaultIfEmpty()
}

export async function listAllUsers() {
  // We don't want to use the firebase-admin api directly since we aren't
  // doing real authorization
  // Authorization is delegated to AWS Cognito
  return callLambda<ListUsersResult, void>(
    'RefittedListFirebaseUsers',
    output => {
      if (typeof output === 'object' && output && 'users' in output)
        return { data: output as ListUsersResult }
      else return { error: 'unknown result format' }
    },
    () => ({ error: 'unhandled empty result' })
  )
}
