import {
  DynamoDBClient,
  GetItemCommand,
  PutItemCommand,
} from '@aws-sdk/client-dynamodb'
import { AttributeValue } from '@aws-sdk/client-dynamodb/dist-types/models'
import { initializeApp, cert, ServiceAccount } from 'firebase-admin/app'
import { RefittedGroup, RefittedGroupIdMapping } from 'refitted'

import serviceAccount from '../firebase.json' assert { type: 'json' }

initializeApp({
  credential: cert(serviceAccount as ServiceAccount),
  databaseURL: 'https://refitted-361ee.firebaseio.com',
})

interface UpdateDynamoGroupEvent {
  group?: RefittedGroup
  addWorkouts?: Array<string>
  removeWorkouts?: Array<string>
}

const tableName = 'refitted.dev01'

/**
 * Updates the DynamoDB group definition mapping a group to workout code names.
 *
 * This is Step 2 of 3 for granting workout access:
 * 1. setUserClaims - Set the "group" claim on the user's token (e.g., "Group1")
 * 2. updateRefittedDynamoGroup - Map the group to workout code names in DynamoDB (helper for client)
 * 3. updateRefittedIamGroup - Update IAM policy to enforce DynamoDB access control
 *
 * This creates/updates a row in the DynamoDB Groups table that maps a group ID to a list
 * of workout code names. The Android client queries this to know which workouts to display.
 *
 * IMPORTANT: This does NOT enforce access control - it's purely a helper for the client
 * to avoid querying workouts it doesn't have access to. The actual enforcement happens
 * in updateRefittedIamGroup via IAM policies.
 *
 * @param group - The RefittedGroup enum value (e.g., RefittedGroup.Group1)
 * @param addWorkouts - Array of workout code names to add to the group
 * @param removeWorkouts - Array of workout code names to remove from the group
 * @returns Comma-separated list of workout code names after update
 */
const updateRefittedDynamoGroup = async ({
  group,
  addWorkouts = [],
  removeWorkouts = [],
}: UpdateDynamoGroupEvent): Promise<string> => {
  if (!group) throw new Error('Group not set')

  const groupKey: Record<string, AttributeValue> = {
    Id: { S: RefittedGroupIdMapping[group] },
    Disc: { S: 'Groups' },
  }

  const dynamoClient = new DynamoDBClient({ region: 'us-east-2' })
  const readCommand = new GetItemCommand({
    TableName: tableName,
    Key: groupKey,
    ProjectionExpression: 'Workouts',
  })
  const { Item: existingResult } = await dynamoClient.send(readCommand)
  const existingWorkouts = existingResult?.Workouts?.SS ?? []
  const updatedWorkouts = new Set(existingWorkouts)
  for (const idx in removeWorkouts) {
    updatedWorkouts.delete(removeWorkouts[idx])
  }
  for (const idx in addWorkouts) {
    updatedWorkouts.add(addWorkouts[idx])
  }

  const upsertCommand = new PutItemCommand({
    TableName: tableName,
    Item: {
      ...groupKey,
      Workouts: { SS: [...updatedWorkouts] },
    },
  })
  await dynamoClient.send(upsertCommand)
  return [...updatedWorkouts].join(',')
}

export const handler = updateRefittedDynamoGroup
