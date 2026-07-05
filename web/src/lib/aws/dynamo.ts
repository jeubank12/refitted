import {
  AttributeValue,
  DynamoDBClient,
  GetItemCommand,
  PutItemCommand,
  QueryCommand,
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

export interface WorkoutPlanSummary {
  name: string
  description?: string
}

// Every workout plan's metadata row lives under the single partition Id="Plan",
// with the program name as the sort key (Disc). Paginates on LastEvaluatedKey
// since there's no known upper bound on the number of plans.
export async function listAllWorkoutPlans(): Promise<WorkoutPlanSummary[]> {
  const plans: WorkoutPlanSummary[] = []
  let exclusiveStartKey: Record<string, AttributeValue> | undefined

  do {
    const { Items, LastEvaluatedKey } = await dynamoClient().send(
      new QueryCommand({
        TableName: tableName,
        KeyConditionExpression: 'Id = :plan',
        ExpressionAttributeValues: { ':plan': { S: 'Plan' } },
        ExclusiveStartKey: exclusiveStartKey,
      })
    )
    for (const item of Items ?? []) {
      const name = item.Disc?.S
      if (!name) continue
      plans.push({ name, description: item.Description?.S })
    }
    exclusiveStartKey = LastEvaluatedKey
  } while (exclusiveStartKey)

  return plans
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
