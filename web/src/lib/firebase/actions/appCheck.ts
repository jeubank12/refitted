'use server'

import { cookies } from 'next/headers'

export async function getAppCheckToken(): Promise<string | undefined> {
  const cookie = cookies().get('session')
  // Use || instead of ?? to handle empty strings (after cookie deletion)
  const session = JSON.parse(cookie?.value || '{}')
  return Promise.resolve(session?.appCheckToken)
}
