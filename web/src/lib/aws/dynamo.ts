import {
  AttributeValue,
  DynamoDBClient,
  GetItemCommand,
  PutItemCommand,
} from '@aws-sdk/client-dynamodb'

const tableName = 'refitted.dev01'

function groupKey(groupId: string): Record<string, AttributeValue> {
  return {
    Id: { S: groupId },
    Disc: { S: 'Groups' },
  }
}

let client: DynamoDBClient | undefined
function dynamoClient() {
  if (!client) client = new DynamoDBClient({ region: 'us-east-2' })
  return client
}

export async function readDynamoGroupWorkouts(
  groupId: string
): Promise<string[]> {
  const { Item } = await dynamoClient().send(
    new GetItemCommand({
      TableName: tableName,
      Key: groupKey(groupId),
      ProjectionExpression: 'Workouts',
    })
  )
  return Item?.Workouts?.SS ?? []
}

export async function writeDynamoGroupWorkouts(
  groupId: string,
  workouts: string[]
): Promise<void> {
  // An SS (string set) attribute cannot be empty - DynamoDB rejects PutItem with SS: [].
  // Omit the attribute entirely when there are no workouts; the read side already
  // treats a missing attribute as an empty list.
  await dynamoClient().send(
    new PutItemCommand({
      TableName: tableName,
      Item: {
        ...groupKey(groupId),
        ...(workouts.length ? { Workouts: { SS: workouts } } : {}),
      },
    })
  )
}

export async function updateDynamoGroupWorkouts(
  groupId: string,
  addWorkouts: string[],
  removeWorkouts: string[]
): Promise<string[]> {
  const existingWorkouts = await readDynamoGroupWorkouts(groupId)
  const updatedWorkouts = new Set(existingWorkouts)
  for (const workout of removeWorkouts) {
    updatedWorkouts.delete(workout)
  }
  for (const workout of addWorkouts) {
    updatedWorkouts.add(workout)
  }
  const result = [...updatedWorkouts]
  await writeDynamoGroupWorkouts(groupId, result)
  return result
}
