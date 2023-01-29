import { DynamoDBClient, GetItemCommand, PutItemCommand } from '@aws-sdk/client-dynamodb'
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
  const {Item: existingResult} = await dynamoClient.send(readCommand)
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
      Workouts: {SS: [...updatedWorkouts]}
    }
  })
  await dynamoClient.send(upsertCommand)
  return [...updatedWorkouts].join(',')
}

export const handler = updateRefittedDynamoGroup
