import {
  CognitoIdentityClient,
  GetIdentityPoolRolesCommand,
} from '@aws-sdk/client-cognito-identity'

let client: CognitoIdentityClient | undefined
function cognitoClient() {
  if (!client) client = new CognitoIdentityClient({ region: 'us-east-2' })
  return client
}

export interface GroupRoleRule {
  groupId: string
  roleArn: string
}

// Paid groups have no CDK-managed resources (see infra/paid-groups.md) - the android
// identity pool's live role-mapping rules ARE the registry of which groups exist and
// which role each one assumes. A "paid group" rule is any rule matching the `group`
// claim by exact value; the static free-email rule and any other match types are not
// paid groups and are skipped.
export async function listGroupRoleRules(): Promise<GroupRoleRule[]> {
  const poolId = process.env.REFITTED_ANDROID_POOL_ID
  if (!poolId) throw new Error('REFITTED_ANDROID_POOL_ID is not set')

  const { RoleMappings } = await cognitoClient().send(
    new GetIdentityPoolRolesCommand({ IdentityPoolId: poolId })
  )

  const rules: GroupRoleRule[] = []
  for (const mapping of Object.values(RoleMappings ?? {})) {
    for (const rule of mapping.RulesConfiguration?.Rules ?? []) {
      if (rule.Claim === 'group' && rule.MatchType === 'Equals' && rule.Value && rule.RoleARN) {
        rules.push({ groupId: rule.Value, roleArn: rule.RoleARN })
      }
    }
  }
  return rules
}
