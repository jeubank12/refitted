import { listAllGroups, resolveGroupPolicyArn } from '../src/lib/aws/groups'
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

  const groups = await listAllGroups()

  if (!group) {
    console.error(
      'Usage: npm run test:groups -- <GroupNameOrId> [--apply] [--add A,B] [--remove C,D]'
    )
    console.error(`Configured groups: ${groups.map(g => `${g.name} (${g.id})`).join(', ')}`)
    process.exit(1)
  }

  const found = groups.find(g => g.name === group || g.id === group)
  if (!found) {
    console.error(`Unknown group: ${group}`)
    console.error(`Configured groups: ${groups.map(g => `${g.name} (${g.id})`).join(', ')}`)
    process.exit(1)
  }

  const policyArn = await resolveGroupPolicyArn(found.id)
  console.log(`Group "${found.name}" -> id=${found.id} policyArn=${policyArn}`)

  console.log('Reading current DynamoDB workout list...')
  const current = await readDynamoGroupWorkouts(found.id)
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
  const dynamoResult = await updateDynamoGroupWorkouts(found.id, add, remove)
  console.log('DynamoDB workouts now:', dynamoResult)

  console.log('\nUpdating IAM policy...')
  const iamResult = await updateIamGroupPolicy(found.id, policyArn, add, remove)
  console.log('IAM policy workouts now:', iamResult)

  console.log('\nDone.')
}

main().catch(error => {
  console.error('test:groups failed:', error)
  process.exit(1)
})
