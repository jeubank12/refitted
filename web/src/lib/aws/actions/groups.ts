'use server'

import { getGroupConfig, getGroupsConfig } from '../groups'
import {
  listAllWorkoutPlans,
  readDynamoGroupWorkouts,
  updateDynamoGroupWorkouts,
  writeDynamoGroupWorkouts,
} from '../dynamo'
import { updateIamGroupPolicy } from '../iam'
import { UpdateGroupResult } from '../types'
import {
  getAuthenticatedAuth,
  serverLogout,
  validateAppCheck,
} from 'src/lib/firebase/actions/auth'

function sanitizeWorkouts(workouts: string[]): string[] {
  return [...new Set(workouts.map(workout => workout.trim()).filter(Boolean))]
}

/**
 * Plan list + per-group Dynamo assignment state for the workout-plans admin
 * page. Deliberately reads only Dynamo (not IAM) for display, matching the
 * requirement that the UI show "just the dynamo state" - IAM is only ever
 * touched by updateGroupWorkouts on write.
 */
export async function getWorkoutPlanAssignments(): Promise<{
  plans: { name: string; description?: string }[]
  groups: { name: string; id: string }[]
  assignments: Record<string, string[]>
}> {
  try {
    await getAuthenticatedAuth()
  } catch (error) {
    const code = (error as { code?: unknown })?.code
    if (typeof code === 'string' && code.startsWith('auth/')) {
      console.debug(
        'Session invalid while listing workout plan assignments, logging out',
        code
      )
      await serverLogout()
    } else {
      console.error('Error authenticating for workout plan assignments:', error)
    }
    throw error
  }

  const plans = await listAllWorkoutPlans()

  // REFITTED_GROUPS_B64 may be unset in some environments (e.g. local dev) -
  // degrade to "no groups" rather than crashing the whole page.
  let groups: { name: string; id: string }[] = []
  try {
    groups = Object.entries(getGroupsConfig()).map(([name, config]) => ({
      name,
      id: config.id,
    }))
  } catch (error) {
    console.warn(
      'Groups config unavailable, plan assignment disabled',
      error
    )
  }

  const assignments: Record<string, string[]> = {}
  await Promise.all(
    groups.map(async group => {
      assignments[group.name] = await readDynamoGroupWorkouts(group.id)
    })
  )

  return { plans, groups, assignments }
}

export async function updateGroupWorkouts({
  group,
  addWorkouts,
  removeWorkouts,
  appCheckToken,
}: {
  group: string
  addWorkouts: string[]
  removeWorkouts: string[]
  appCheckToken: string
}): Promise<UpdateGroupResult> {
  const validationError = await validateAppCheck(appCheckToken)
  if (validationError) {
    console.error('updateGroupWorkouts refused', validationError)
    return { status: 'app-check-failed', error: validationError }
  }

  try {
    await getAuthenticatedAuth()
  } catch (error) {
    console.error('Session invalid during updateGroupWorkouts', error)
    await serverLogout()
    return { status: 'unauthorized', error: 'Not authorized' }
  }

  let groupConfig
  try {
    groupConfig = getGroupConfig(group)
  } catch {
    return { status: 'unknown-group', error: `Unknown group: ${group}` }
  }

  const add = sanitizeWorkouts(addWorkouts)
  const remove = sanitizeWorkouts(removeWorkouts)

  let snapshot: string[]
  try {
    snapshot = await readDynamoGroupWorkouts(groupConfig.id)
  } catch (error) {
    console.error('Failed to read current workout list', error)
    return { status: 'dynamo-failed', error: 'Failed to read current workout list' }
  }

  let dynamoResult: string[]
  try {
    dynamoResult = await updateDynamoGroupWorkouts(groupConfig.id, add, remove)
  } catch (error) {
    console.error('Dynamo update failed', error)
    return { status: 'dynamo-failed', error: 'Failed to update workout list' }
  }

  // IAM policy-version management is far harder to reverse than a Dynamo
  // re-PutItem, so it runs last and only Dynamo (the easy half) is ever
  // compensated if it fails.
  try {
    const iamResult = await updateIamGroupPolicy(
      groupConfig.id,
      groupConfig.policyArn,
      add,
      remove
    )
    return { status: 'ok', dynamo: dynamoResult, iam: iamResult }
  } catch (error) {
    console.error('IAM update failed, rolling back Dynamo', error)
    try {
      await writeDynamoGroupWorkouts(groupConfig.id, snapshot)
      return {
        status: 'iam-failed-rolled-back',
        error: 'Failed to update access policy; workout list change was rolled back',
      }
    } catch (rollbackError) {
      console.error('Dynamo rollback also failed', rollbackError)
      return {
        status: 'iam-failed-rollback-failed',
        error: 'Failed to update access policy',
        rollbackError:
          rollbackError instanceof Error
            ? rollbackError.message
            : 'Failed to roll back workout list',
      }
    }
  }
}
