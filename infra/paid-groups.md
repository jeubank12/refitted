# Paid groups: architecture, manual runbook, and recommended automation

## Architecture

A paid group is the union of three AWS resources plus one DynamoDB row. None of it lives
in CDK (`infra/lib/auth-stack.ts`) — it's admin-owned live data, created and edited
directly against AWS APIs by the web admin app.

| Resource | Naming convention | Purpose |
|---|---|---|
| IAM role | `Cognito_refitted_group-<uuid>` | Assumed by the android Cognito identity pool for members of this group |
| IAM managed policy | `DynamoDb-Refitted.Dev01-Group-<uuid>` | `GetItem`/`Query` on the table, scoped to `LeadingKeys: [<uuid>, ...planNames]` |
| Cognito role-mapping rule | `{claim: 'group', matchType: 'Equals', value: <uuid>, roleArn}` | Routes a Firebase user whose `group` claim equals `<uuid>` to the role above |
| DynamoDB row | `{Id: <uuid>, Disc: 'Groups', Name?: <display name>, Workouts?: <SS>}` | Display name + assigned plans (read by both the admin UI and the Android app — see `DynamoWorkoutPlanNetworkService.kt:42-52`) |

The web app enumerates paid groups by reading these live resources, not a repo-side list:
`GetIdentityPoolRoles` on the android pool returns every `group`/`Equals` rule
(`src/lib/aws/cognito.ts:listGroupRoleRules`), each rule's `roleArn` resolves to its
policy via `ListAttachedRolePolicies` (`src/lib/aws/iam.ts:getAttachedGroupPolicyArn`),
and display names come from the Dynamo row (`src/lib/aws/dynamo.ts:listGroupNames`,
falling back to the raw uuid if unset).

**Why CDK doesn't own any of this**: it used to — a dedicated role, an
`Equals <uuid>` rule, and a policy ARN reference per paid group, all in
`auth-stack.ts`. Every new paid group meant a CDK deploy, and the number of paid groups
was structurally visible in the stack. Free and Anon still work this way (they're
claim-less fallback paths, not something an admin creates — see
`DynamoWorkoutPlanNetworkService.kt:48-49` — so there will only ever be exactly two of
them; keeping them in CDK costs nothing and needs no runtime discovery).

**Permissions boundary**: every group role must have `Refitted-GroupRoleBoundary`
(CDK-managed, `auth-stack.ts`) attached as its `PermissionsBoundary`. The boundary caps
the role's *effective* permissions to Dynamo read-only on the table, regardless of what
its own attached policy grants. This means a bug in group-creation tooling, or a policy
statement that accidentally widens beyond `LeadingKeys` scoping, still can't turn a group
role into anything more than "reads workout data" — no IAM, no writes, no other AWS
services. `androidAuthRole` and `androidFreeRole` also carry this boundary today.

## Why not a shared role + Cognito principal tags

This was tried and conclusively disproven in an earlier session: mapping the Firebase
`group` claim to an `aws:PrincipalTag/group` session tag via Cognito's "attributes for
access control," then discriminating groups with a single shared role + one IAM
`Condition` per group in one shared policy. Every piece of config was verified correct
(trust policy `sts:TagSession`, `SetPrincipalTagAttributeMap`, role-mapping rule) but the
tag was never actually attached to the assumed-role session — confirmed by minting a real
Firebase ID token, calling `GetCredentialsForIdentity` directly, and observing
`AccessDeniedException` with the tag condition in place that disappeared the instant the
condition was removed. Cognito's attributes-for-access-control does not compose with
Rules-based role mapping for a third-party OIDC provider (Firebase) via
`GetCredentialsForIdentity` in this account. **Do not retry this approach** without a
fundamentally different credential-vending path (e.g. having Android call
`GetCredentialsForIdentity` directly instead of through the legacy AWS Mobile SDK's
`CognitoCachingCredentialsProvider`).

## Manual runbook: creating a paid group today

No admin UI exists yet for group *creation* (only workout-plan *assignment* to existing
groups, and user *claim* assignment — see `web/app/admin/workouts` and
`web/app/admin/users`). Until the automation below is built, create a group with the AWS
CLI, with the user watching each step:

```bash
GROUP_ID=$(uuidgen)   # or any UUID; this becomes the Firebase `group` claim value
ACCOUNT_ID=<account>
BOUNDARY_ARN="arn:aws:iam::${ACCOUNT_ID}:policy/Refitted-GroupRoleBoundary"
POOL_ID=<android identity pool id>

# 1. Policy: same statement shape as Free/Anon, scoped to this group's uuid.
aws iam create-policy \
  --policy-name "DynamoDb-Refitted.Dev01-Group-${GROUP_ID}" \
  --policy-document file://policy.json   # Allow GetItem/Query on the table + Reverse-index,
                                          # Condition ForAllValues:StringEquals
                                          # dynamodb:LeadingKeys = ["${GROUP_ID}"]

# 2. Role: same trust policy shape as the old Paid1 role, boundary attached.
aws iam create-role \
  --role-name "Cognito_refitted_group-${GROUP_ID}" \
  --permissions-boundary "$BOUNDARY_ARN" \
  --assume-role-policy-document file://trust-policy.json   # Federated
                                                             # cognito-identity.amazonaws.com,
                                                             # sts:AssumeRoleWithWebIdentity,
                                                             # aud = $POOL_ID,
                                                             # amr = securetoken.google.com/refitted-361ee

aws iam attach-role-policy \
  --role-name "Cognito_refitted_group-${GROUP_ID}" \
  --policy-arn "arn:aws:iam::${ACCOUNT_ID}:policy/DynamoDb-Refitted.Dev01-Group-${GROUP_ID}"

# 3. Rule: read-modify-write the pool's role mapping - prepend before the free-email rule.
aws cognito-identity get-identity-pool-roles --identity-pool-id "$POOL_ID" > current.json
# edit current.json: prepend {claim: "group", matchType: "Equals", value: "$GROUP_ID",
# roleArn: "arn:aws:iam::${ACCOUNT_ID}:role/Cognito_refitted_group-${GROUP_ID}"}
# to RoleMappings[...].RulesConfiguration.Rules, keep everything else identical
aws cognito-identity set-identity-pool-roles --cli-input-json file://current.json

# 4. Dynamo row (name is the user's IP - never put it in CDK/repo).
aws dynamodb put-item --table-name refitted.dev01 --item '{
  "Id": {"S": "'"$GROUP_ID"'"}, "Disc": {"S": "Groups"}, "Name": {"S": "<display name>"}
}'
```

Verify: `aws cognito-identity get-identity-pool-roles` shows the new rule ahead of the
free rule; the admin UI's workout-plans page shows the new group as a column
(`src/lib/aws/groups.ts:listAllGroups`); assigning a plan to it round-trips through
`updateGroupWorkouts` exactly like Free/Anon.

**Cap**: Cognito allows at most 25 role-mapping rules per identity provider. One rule is
the static free-email rule, so there's room for roughly 24 paid groups before a second
mechanism (e.g. spilling to a second identity provider, or a genuinely shared-role
redesign) becomes necessary.

## Recommended next step: a provisioning Lambda

The manual runbook above is real AWS write access (`CreateRole`, `PassRole`,
`SetIdentityPoolRoles`) that would otherwise have to live on the web app's own ambient
credentials if this were automated directly in `web/`. That's a meaningfully larger blast
radius for a leaked `.env.local` key than what the web app carries today (`iam:*PolicyVersion`
on existing policies only): `SetIdentityPoolRoles` + `PassRole` together can re-route an
attacker-controlled Firebase account to any *existing* role in the pool, including every
paid group's role — which is exactly the workout-program IP this whole effort protects.

Recommended shape when this gets built:

- A small CDK-managed Lambda, `RefittedProvisionGroup`. Input: `{ groupId: <uuid> }` only
  — never a name, so display names (the user's IP) never pass through Lambda code, env
  vars, or logs. Output: `{ roleArn, policyArn }`.
- The function body performs exactly the four steps in the manual runbook above,
  hardcoded (role trust policy shape, policy statement shape, rule shape, boundary ARN).
  Idempotent — safe to retry with the same `groupId`.
- Its execution role holds the boundary-conditioned `iam:CreateRole` (conditioned on
  `iam:PermissionsBoundary` = the boundary ARN), `iam:CreatePolicy`, `iam:AttachRolePolicy`,
  `iam:PassRole` (conditioned on `iam:PassedToService = cognito-identity.amazonaws.com`,
  scoped to `Cognito_refitted_*` roles plus every role already in the pool config), and
  `cognito-identity:{Get,Set}IdentityPoolRoles`.
- The web app gets **`lambda:InvokeFunction` on this one function ARN only** — explicitly
  never `lambda:UpdateFunctionCode`/`UpdateFunctionConfiguration`. A leaked web credential
  can invoke the fixed operation; it cannot redefine what invoking it does. Only a CDK
  deploy (the developer's own credentials) can change the function body.
- This narrows the question from "are five interlocking IAM `Condition`s on the web
  principal airtight?" to "does this one small, reviewable function do anything besides
  the four steps above?" — a much easier thing to keep correct over time.

Group *deletion* is out of scope for this automation for now (rare, and needs a decision
about what happens to a role's still-live sessions and any user still carrying the claim)
and can continue to be a manual, careful CLI operation.
