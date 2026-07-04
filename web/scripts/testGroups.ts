import { getGroupConfig, getGroupNames } from '../src/lib/aws/groups'
import { readDynamoGroupWorkouts, updateDynamoGroupWorkouts } from '../src/lib/aws/dynamo'
import { updateIamGroupPolicy } from '../src/lib/aws/iam'

function parseArgs(argv: string[]) {
  const [group, ...rest] = argv
  const apply = rest.includes('--apply')
  const addIndex = rest.indexOf('--add')
  const removeIndex = rest.indexOf('--remove')
  const add =
    addIndex >= 0 ? (rest[addIndex + 1] ?? '').split(',').filter(Boolean) : []
  const remove =
    removeIndex >= 0
      ? (rest[removeIndex + 1] ?? '').split(',').filter(Boolean)
      : []
  return { group, apply, add, remove }
}

async function main() {
  const { group, apply, add, remove } = parseArgs(process.argv.slice(2))

  if (!group) {
    console.error(
      'Usage: npm run test:groups -- <GroupName> [--apply] [--add A,B] [--remove C,D]'
    )
    console.error(`Configured groups: ${getGroupNames().join(', ')}`)
    process.exit(1)
  }

  const config = getGroupConfig(group)
  console.log(`Group "${group}" -> id=${config.id} policyArn=${config.policyArn}`)

  console.log('Reading current DynamoDB workout list...')
  const current = await readDynamoGroupWorkouts(config.id)
  console.log('Current workouts:', current)

  if (!apply) {
    console.log('\nDry run only (pass --apply to mutate). No changes made.')
    return
  }

  if (!add.length && !remove.length) {
    console.log('\n--apply passed with no --add/--remove; nothing to change.')
    return
  }

  console.warn(
    '\n--apply set: this WILL mutate real DynamoDB rows and create a new IAM policy version (max 5 kept).'
  )
  console.log(`Adding: ${add.join(', ') || '(none)'}`)
  console.log(`Removing: ${remove.join(', ') || '(none)'}`)

  console.log('\nUpdating DynamoDB...')
  const dynamoResult = await updateDynamoGroupWorkouts(config.id, add, remove)
  console.log('DynamoDB workouts now:', dynamoResult)

  console.log('\nUpdating IAM policy...')
  const iamResult = await updateIamGroupPolicy(
    config.id,
    config.policyArn,
    add,
    remove
  )
  console.log('IAM policy workouts now:', iamResult)

  console.log('\nDone.')
}

main().catch(error => {
  console.error('test:groups failed:', error)
  process.exit(1)
})
