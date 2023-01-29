import {
  IAMClient,
  CreatePolicyVersionCommand,
  DeletePolicyVersionCommand,
  GetPolicyVersionCommand,
  ListPolicyVersionsCommand,
} from '@aws-sdk/client-iam'
import { initializeApp, cert, ServiceAccount } from 'firebase-admin/app'
import {
  RefittedGroup,
  RefittedGroupIdMapping,
  RefittedGroupPolicyMapping,
} from 'refitted'

import serviceAccount from '../firebase.json' assert { type: 'json' }

initializeApp({
  credential: cert(serviceAccount as ServiceAccount),
  databaseURL: 'https://refitted-361ee.firebaseio.com',
})

interface UpdateIamGroupEvent {
  group?: RefittedGroup
  addWorkouts?: Array<string>
  removeWorkouts?: Array<string>
}

interface PolicyStatement {
  Effect: string
  Action: Array<string>
  Resource: Array<string>
  Condition: {
    'ForAllValues:StringEquals': {
      'dynamodb:LeadingKeys': Array<string>
    }
  }
}

interface PolicyDocument {
  Version: string
  Statement: Array<PolicyStatement>
}

const updateRefittedIamGroup = async ({
  group,
  addWorkouts = [],
  removeWorkouts = [],
}: UpdateIamGroupEvent) => {
  if (!group) throw new Error('Group not set')

  const policyArn = RefittedGroupPolicyMapping[group]

  const iamClient = new IAMClient({})
  const policyVersionsCommand = new ListPolicyVersionsCommand({
    PolicyArn: policyArn,
  })
  const { Versions: existingVersions } = await iamClient.send(
    policyVersionsCommand
  )

  const sortedVersions =
    existingVersions
      ?.map(version => version.VersionId)
      .filter((version): version is string => !!version)
      .sort((a, b) => {
        return a.localeCompare(b)
      }) ?? []

  if (sortedVersions.length > 1) {
    const oldestVersionId = sortedVersions[0]
    const deleteOldestVersionCommand = new DeletePolicyVersionCommand({
      PolicyArn: policyArn,
      VersionId: oldestVersionId,
    })
    await iamClient.send(deleteOldestVersionCommand)
  }

  if (!sortedVersions.length)
    throw new Error('No defined versions for this policy')

  const currentVersionId = sortedVersions.at(-1)
  const getCurrentVersionCommand = new GetPolicyVersionCommand({
    PolicyArn: policyArn,
    VersionId: currentVersionId,
  })
  const currentVersion = await iamClient.send(getCurrentVersionCommand)
  const currentPolicyDocument = currentVersion.PolicyVersion?.Document

  if (!currentPolicyDocument) throw new Error('Current Policy not found')

  const currentPolicy = JSON.parse(
    decodeURIComponent(currentPolicyDocument)
  ) as PolicyDocument

  const existingStatement = currentPolicy.Statement[0]
  const existingWorkouts =
    existingStatement.Condition['ForAllValues:StringEquals'][
      'dynamodb:LeadingKeys'
    ]
  const updatedWorkouts = new Set(existingWorkouts)
  for (const idx in removeWorkouts) {
    updatedWorkouts.delete(removeWorkouts[idx])
  }
  for (const idx in addWorkouts) {
    updatedWorkouts.add(addWorkouts[idx])
  }

  const updatedPolicy: PolicyDocument = {
    ...currentPolicy,
    Statement: [
      {
        ...existingStatement,
        Condition: {
          'ForAllValues:StringEquals': {
            'dynamodb:LeadingKeys': [
              RefittedGroupIdMapping[group],
              ...updatedWorkouts,
            ],
          },
        },
      },
    ],
  }

  const createPolicyVersionCommand = new CreatePolicyVersionCommand({
    PolicyArn: policyArn,
    PolicyDocument: JSON.stringify(updatedPolicy),
    SetAsDefault: true,
  })
  await iamClient.send(createPolicyVersionCommand)
}

export const handler = updateRefittedIamGroup
