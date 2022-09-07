import { createSelector } from 'reselect'

import { LambdaClient } from '@aws-sdk/client-lambda'

import { getAwsCredentials } from '../cognito/cognitoSelectors'

export const getLambdaClient = createSelector(
  [getAwsCredentials],
  credentials => {
    if (credentials)
      return new LambdaClient({ region: 'us-east-2', credentials })
  }
)
