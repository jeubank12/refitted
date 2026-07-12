import { getWorkoutPlanAssignments } from 'src/lib/aws/actions/groups'
import WorkoutPlansTable from './WorkoutPlansTable'

/**
 * Workout plans list page - displays every plan in Dynamo and which groups
 * (paid tiers) it's currently assigned to.
 *
 * getWorkoutPlanAssignments() rethrows on auth failure (after revoking the
 * session), which surfaces Next's nearest error boundary rather than
 * redirecting from here - same pattern as the users page.
 */
export default async function WorkoutPlansList() {
  const { plans, groups, assignments } = await getWorkoutPlanAssignments()

  if (!plans.length) return <div>empty</div>

  return (
    <WorkoutPlansTable plans={plans} groups={groups} assignments={assignments} />
  )
}
