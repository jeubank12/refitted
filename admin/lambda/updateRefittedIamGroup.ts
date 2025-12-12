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

/**
 * Updates the IAM policy to enforce DynamoDB access control for a group.
 *
 * This is Step 3 of 3 for granting workout access:
 * 1. setUserClaims - Set the "group" claim on the user's token (e.g., "Group1")
 * 2. updateRefittedDynamoGroup - Map the group to workout code names in DynamoDB (helper for client)
 * 3. updateRefittedIamGroup - Update IAM policy to enforce DynamoDB access control
 *
 * This updates the AWS IAM policy associated with the group to allow DynamoDB queries
 * only for specific workout code names. It modifies the 'dynamodb:LeadingKeys' condition
 * to include the group ID and the allowed workout code names.
 *
 * IMPORTANT: This is the actual enforcement mechanism. Without the correct IAM policy,
 * DynamoDB will reject queries from the Android client even if the client knows about
 * the workout code names from the DynamoDB group definition.
 *
 * The function automatically manages IAM policy versions (AWS allows max 5 versions),
 * deleting the oldest non-default version before creating a new one if needed.
 *
 * @param group - The RefittedGroup enum value (e.g., RefittedGroup.Group1)
 * @param addWorkouts - Array of workout code names to grant access to
 * @param removeWorkouts - Array of workout code names to revoke access from
 */
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
