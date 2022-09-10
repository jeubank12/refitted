import { createSelector } from 'reselect'

import { lambdaExtendedApi } from './lambdaEndpoints'

export const getUserCustomClaimTypes = createSelector(
  [lambdaExtendedApi.endpoints.getUsers.select()],
  cache => {
    const allCustomClaims =
      cache.data?.users?.map(user => user.customClaims ?? {}) ?? []
    const customClaimsKeys = allCustomClaims
      .map(claims => Object.keys(claims))
      .reduce((agg, claims) => {
        return [...agg, ...claims]
      }, [])
    return [...new Set(customClaimsKeys)]
  }
)
