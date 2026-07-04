import {
  CreatePolicyVersionCommand,
  DeletePolicyVersionCommand,
  GetPolicyVersionCommand,
  IAMClient,
  ListPolicyVersionsCommand,
  PolicyVersion,
} from '@aws-sdk/client-iam'

interface PolicyStatement {
  Effect: string
  Action: string[]
  Resource: string[]
  Condition: {
    'ForAllValues:StringEquals': {
      'dynamodb:LeadingKeys': string[]
    }
  }
}

interface PolicyDocument {
  Version: string
  Statement: PolicyStatement[]
}

let client: IAMClient | undefined
function iamClient() {
  if (!client) client = new IAMClient({ region: 'us-east-2' })
  return client
}

function versionNumber(versionId?: string): number {
  return parseInt((versionId ?? '').slice(1), 10)
}

export async function updateIamGroupPolicy(
  groupId: string,
  policyArn: string,
  addWorkouts: string[],
  removeWorkouts: string[]
): Promise<string[]> {
  const { Versions: versions } = await iamClient().send(
    new ListPolicyVersionsCommand({ PolicyArn: policyArn })
  )
  const sortedVersions: PolicyVersion[] = (versions ?? []).sort(
    (a, b) => versionNumber(a.VersionId) - versionNumber(b.VersionId)
  )

  if (!sortedVersions.length) {
    throw new Error('No defined versions for this policy')
  }

  // IAM allows a maximum of 5 versions per policy; delete the oldest NON-DEFAULT
  // version to make room. Never delete the default version - the lambda this was
  // ported from blindly deleted the numerically-oldest version, which errors if
  // that happens to be the default.
  if (sortedVersions.length > 1) {
    const oldestNonDefault = sortedVersions.find(v => !v.IsDefaultVersion)
    if (oldestNonDefault?.VersionId) {
      await iamClient().send(
        new DeletePolicyVersionCommand({
          PolicyArn: policyArn,
          VersionId: oldestNonDefault.VersionId,
        })
      )
    }
  }

  const defaultVersion = sortedVersions.find(v => v.IsDefaultVersion)
  if (!defaultVersion?.VersionId) {
    throw new Error('No default version found for this policy')
  }

  const { PolicyVersion: currentVersion } = await iamClient().send(
    new GetPolicyVersionCommand({
      PolicyArn: policyArn,
      VersionId: defaultVersion.VersionId,
    })
  )
  const document = currentVersion?.Document
  if (!document) throw new Error('Current policy document not found')

  const currentPolicy = JSON.parse(
    decodeURIComponent(document)
  ) as PolicyDocument
  const existingStatement = currentPolicy.Statement[0]
  const workouts = new Set(
    existingStatement.Condition['ForAllValues:StringEquals'][
      'dynamodb:LeadingKeys'
    ]
  )
  // LeadingKeys always carries the group id alongside workout names; remove it
  // before diffing so it isn't duplicated when re-prepended below (the lambda
  // this was ported from accumulated duplicate groupId entries over time).
  workouts.delete(groupId)
  for (const workout of removeWorkouts) {
    workouts.delete(workout)
  }
  for (const workout of addWorkouts) {
    workouts.add(workout)
  }
  const updatedWorkouts = [...workouts]

  const updatedPolicy: PolicyDocument = {
    ...currentPolicy,
    Statement: [
      {
        ...existingStatement,
        Condition: {
          'ForAllValues:StringEquals': {
            'dynamodb:LeadingKeys': [groupId, ...updatedWorkouts],
          },
        },
      },
    ],
  }

  await iamClient().send(
    new CreatePolicyVersionCommand({
      PolicyArn: policyArn,
      PolicyDocument: JSON.stringify(updatedPolicy),
      SetAsDefault: true,
    })
  )

  return updatedWorkouts
}
