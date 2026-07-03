# Refitted Infra (CDK)

CDK v2 (TypeScript) managing the Refitted AWS backend: DynamoDB table and
Cognito identity pools + IAM roles/policies. Replaces a prior ad-hoc
Terraform import exercise kept outside this repo.

The admin Lambda functions (`admin/lambda/*.ts`) are deliberately **not**
managed by CDK — they're slated for removal/replacement, so there's no value
in importing them here. If that changes, add a stack for them then.

Stacks (deploy in order — `RefittedAuth` depends on `RefittedDatabase`'s
table ARN export):

| Stack             | File                      | Owns |
|--------------------|---------------------------|------|
| `RefittedDatabase` | `lib/database-stack.ts`  | DynamoDB table `refitted.dev01` + GSIs |
| `RefittedAuth`     | `lib/auth-stack.ts`      | Cognito identity pools, IAM roles, IAM policies, role-mapping rules |

## Context values

`paid1GroupId` (the Firebase custom-claim group UUID for the Paid1 tier) is
kept out of the repo via `cdk.context.json`, which is gitignored. Create it
locally:

```json
{
  "paid1GroupId": "<uuid>"
}
```

Or pass it inline: `cdk deploy -c paid1GroupId=<uuid>`.

To find/verify the live value (e.g. after a suspected drift), pull it from
the deployed Cognito role mapping — this is the authoritative source, not
CDK:

```bash
aws cognito-identity list-identity-pools --max-results 20
aws cognito-identity get-identity-pool-roles --identity-pool-id <android-pool-id>
# → RoleMappings...RulesConfiguration.Rules[].Value for Claim "group"
```

To compare any deployed IAM policy's live document against what's in Git
(useful before a `cdk deploy` if you suspect drift):

```bash
ARN=$(aws iam list-policies --query "Policies[?PolicyName=='<name>'].Arn" --output text)
VER=$(aws iam get-policy --policy-arn $ARN --query "Policy.DefaultVersionId" --output text)
aws iam get-policy-version --policy-arn $ARN --version-id $VER --query "PolicyVersion.Document"
```

## Ownership boundary: CDK vs. admin Lambdas

**This is the most important thing to understand before touching `auth-stack.ts`.**

The Paid1 DynamoDB access policy (`DynamoDb-Refitted.Dev01-Paid1`) has a
`dynamodb:LeadingKeys` condition listing which workout programs that tier can
read. That list is **live, admin-mutable application data — and the actual
program names are proprietary content, not open-source app code.** This repo
is public; the workout program catalog is not. The `RefittedUpdateIamGroup`
Lambda (`admin/lambda/updateRefittedIamGroup.ts`) rewrites the `LeadingKeys`
list as a new IAM policy version whenever a program is added/removed through
the admin tooling, and `RefittedUpdateDynamoGroup` does the equivalent for
the corresponding DynamoDB group item's workout list. **No program name
should ever be written into CDK source, `cdk.context.json`, or this repo in
any form** — the policy/data content only ever exists in live AWS state,
written there exclusively by the admin Lambdas.

This split is permanent, not a stopgap: the long-term plan is for the
website's admin UI to be the tool for managing the program catalog (calling
these same Lambdas). CDK's job stays scoped to infrastructure shape — it
should never grow into managing program content, now or later.

Which groups those Lambdas manage is defined in `admin/refitted.ts`
(`RefittedGroup` enum + `RefittedGroupIdMapping` / `RefittedGroupPolicyMapping`).
Today only `Paid1` is in that enum.

**Rule: if a policy's content can be rewritten by an admin Lambda, CDK must
never declare that policy's document.** If it did, the next `cdk deploy`
would create a new policy version reverting to whatever was baked into the
CDK code, silently undoing real admin changes. `auth-stack.ts` currently
treats Paid1/Free/Anon uniformly this way (referenced by ARN via
`iam.ManagedPolicy.fromManagedPolicyArn`, never created/updated by CDK) —
kept uniform across all three even though only Paid1 is presently
Lambda-managed, so adding Free/Anon to the enum later doesn't require an
infra change.

CDK *does* own: the roles themselves (trust policy, name), which policy ARN
gets attached to which role, the identity pool role-mapping rules (which
Firebase claim routes to which role), and any policy with fully static
content (`DynamoDb-Refitted.Dev01-Read-Only`, `IAM-Refitted-EditGroups`,
`Refitted-InvokeFirebaseAdminLambda`, `DynamoDb-Role-Permissions-Testing`).

There's no native CloudFormation "create once, then ignore forever" mode for
a resource like this — every deploy reconciles to the template. The only way
to get that behavior in CDK is an `AwsCustomResource` with only
`onCreate`/`onDelete` handlers (no `onUpdate`), which is Lambda-backed
machinery we've deliberately avoided for the (rare) new-role bootstrap case
below.

## Runbook: adding a new role/tier

Example: adding a hypothetical "Paid2" tier.

1. **Bootstrap the IAM policy outside CDK** (one-time, manual — CDK never
   creates group-scoped policies, see above):
   ```bash
   aws iam create-policy --policy-name DynamoDb-Refitted.Dev01-Paid2 \
     --policy-document file://initial-paid2-policy.json
   ```
   Minimal starting document: same shape as the existing Paid1 policy, with
   `dynamodb:LeadingKeys` containing just the new group's UUID. Keep
   `initial-paid2-policy.json` local/uncommitted — same rule as above, no
   program names in the repo.

2. **`admin/refitted.ts`**: add the new value to `RefittedGroup`, and entries
   in `RefittedGroupIdMapping` / `RefittedGroupPolicyMapping` pointing at the
   UUID and policy ARN from step 1. No Lambda code changes needed — both
   admin Lambdas are already generic over `group`.

3. **`infra/lib/auth-stack.ts`**:
   - Add `const paid2PolicyArn = ...` + `iam.ManagedPolicy.fromManagedPolicyArn(...)`.
   - Add a new `iam.Role` (`Cognito_refitted_androidPaid2_Role`) with the
     same `WebIdentityPrincipal` trust pattern as `androidPaid1Role`, and
     attach the new policy.
   - Add a rule to the `AndroidPoolRoles` `CfnIdentityPoolRoleAttachment`
     mapping the new group's Firebase claim value to the new role.

4. `cdk deploy RefittedAuth` (context: pass the new group id the same way as
   `paid1GroupId` if you want it out of source).

## Import notes

For a stack with cross-stack references (`RefittedAuth` reads
`RefittedDatabase`'s table ARN via `Fn::ImportValue`), the producer stack
must already be **deployed** with that value exported before the consumer
can import — `cdk import` synthesizes the whole app, but an export only
exists in the producer's live template once something has actually consumed
it in a real deploy. If you hit `No export named ... found` mid-import,
`cdk deploy` the producer stack first (safe — it only adds the missing
Output), then retry the import.

`AWS::Cognito::IdentityPoolRoleAttachment` **does** support CloudFormation
import (primary identifier `Id`, which equals the identity pool ID) — despite
an earlier assumption in this repo's history that it didn't. What it does
**not** support is idempotent create: declaring it fresh against a pool that
already has a live role mapping fails with `AlreadyExists` rather than
upserting. Before importing a resource type you're unsure about, confirm its
identifier against live state with Cloud Control API rather than guessing:
`aws cloudcontrol get-resource --type-name <Type> --identifier <id>`. Also
useful: `aws cloudformation describe-type --type-name <Type> --type RESOURCE
--query Schema` to get the exact `primaryIdentifier` property name `cdk
import --resource-mapping` expects.

A failed `cdk import` can leave the CloudFormation stack in
`ROLLBACK_COMPLETE` (or `IMPORT_ROLLBACK_COMPLETE`), which blocks retrying
until deleted. This is safe to delete without any AWS-side impact if the
stack held zero resources when it failed (check with
`list-stack-resources` first):

```bash
aws cloudformation describe-stacks --stack-name RefittedAuth --query "Stacks[0].StackStatus"
aws cloudformation list-stack-resources --stack-name RefittedAuth
aws cloudformation delete-stack --stack-name RefittedAuth
aws cloudformation wait stack-delete-complete --stack-name RefittedAuth
```
