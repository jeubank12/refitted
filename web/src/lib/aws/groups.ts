import { listGroupRoleRules } from './cognito'
import { listGroupNames } from './dynamo'
import { getAttachedGroupPolicyArn } from './iam'

// Free/Anon are automatic fallbacks applied when a user has no `group` claim (free) or is
// a Firebase-anonymous user (anon) - see DynamoWorkoutPlanNetworkService.kt:48-49. They're
// never assigned by an admin, so they're excluded from listAssignableGroups() but still
// need static ids/policies for workout-plan assignment (see listAllGroups()).
export const FREE_GROUP_ID = 'free'
export const ANON_GROUP_ID = 'anon'

export interface Group {
  id: string
  name: string
}

function accountId(): string {
  const id = process.env.REFITTED_AWS_ACCOUNT_ID
  if (!id) throw new Error('REFITTED_AWS_ACCOUNT_ID is not set')
  return id
}

function staticPolicyArn(policyName: string): string {
  return `arn:aws:iam::${accountId()}:policy/${policyName}`
}

const STATIC_GROUPS: Group[] = [
  { id: FREE_GROUP_ID, name: 'Free' },
  { id: ANON_GROUP_ID, name: 'Anon' },
]

export function staticGroups(): Group[] {
  return STATIC_GROUPS
}

// Paid groups have no CDK-managed registry (see infra/paid-groups.md) - the android
// identity pool's live `group`-claim role-mapping rules ARE the list of paid groups that
// exist. Display names come from the Dynamo Groups row when set, falling back to the raw
// id so a group is never silently hidden just because its Name wasn't written.
export async function listPaidGroups(): Promise<Group[]> {
  const [rules, names] = await Promise.all([listGroupRoleRules(), listGroupNames()])
  return rules.map(({ groupId }) => ({ id: groupId, name: names[groupId] ?? groupId }))
}

// Assignable to a user's `group` claim - paid groups only. Free/Anon are automatic
// fallbacks, never something an admin manually assigns.
export async function listAssignableGroups(): Promise<Group[]> {
  return listPaidGroups()
}

// Every group a workout plan can be assigned to - static Free/Anon plus every paid group.
export async function listAllGroups(): Promise<Group[]> {
  const paid = await listPaidGroups()
  return [...STATIC_GROUPS, ...paid]
}

export async function resolveGroupPolicyArn(groupId: string): Promise<string> {
  if (groupId === FREE_GROUP_ID) return staticPolicyArn('DynamoDb-Refitted.Dev01-Free')
  if (groupId === ANON_GROUP_ID) return staticPolicyArn('DynamoDb-Refitted.Dev01-Anon')

  const rules = await listGroupRoleRules()
  const rule = rules.find(r => r.groupId === groupId)
  if (!rule) throw new Error(`Unknown group: ${groupId}`)
  return getAttachedGroupPolicyArn(rule.roleArn)
}
