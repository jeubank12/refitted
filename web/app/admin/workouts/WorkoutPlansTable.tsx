'use client'
import { useMemo, useState, useTransition } from 'react'

import { useRouter } from 'next/navigation'

import { DataGrid, GridColDef } from '@mui/x-data-grid'
import Paper from '@mui/material/Paper'
import Box from '@mui/material/Box'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import Checkbox from '@mui/material/Checkbox'
import Snackbar from '@mui/material/Snackbar'
import Alert from '@mui/material/Alert'

import { useAppCheckToken } from 'src/lib/firebase/appCheck'
import { updateGroupWorkouts } from 'src/lib/aws/actions/groups'

type WorkoutPlan = { name: string; description?: string }
type Group = { name: string; id: string }

type Row = WorkoutPlan & { id: string }

export default function WorkoutPlansTable({
  plans,
  groups,
  assignments,
}: {
  plans: WorkoutPlan[]
  groups: Group[]
  assignments: Record<string, string[]>
}) {
  const router = useRouter()
  const { getAppCheckToken } = useAppCheckToken()
  const [isPending, startTransition] = useTransition()
  // Keyed by `${planName}::${groupName}` so an in-flight toggle only disables
  // that one cell, not every group column for the plan's row.
  const [pendingKey, setPendingKey] = useState<string | null>(null)
  const [notice, setNotice] = useState<{
    severity: 'success' | 'error'
    message: string
  } | null>(null)

  const rows = useMemo<Row[]>(
    () => plans.map(plan => ({ id: plan.name, ...plan })),
    [plans]
  )

  const assignmentSets = useMemo(
    () =>
      Object.fromEntries(
        groups.map(group => [group.id, new Set(assignments[group.id] ?? [])])
      ),
    [groups, assignments]
  )

  const handleToggle = (
    planName: string,
    groupId: string,
    groupName: string,
    assign: boolean
  ) => {
    const key = `${planName}::${groupId}`
    setPendingKey(key)
    startTransition(async () => {
      try {
        const token = await getAppCheckToken()
        const result = await updateGroupWorkouts({
          group: groupId,
          addWorkouts: assign ? [planName] : [],
          removeWorkouts: assign ? [] : [planName],
          appCheckToken: token,
        })
        if (result.status === 'ok') {
          setNotice({
            severity: 'success',
            message: `${assign ? 'Assigned' : 'Removed'} "${planName}" ${
              assign ? 'to' : 'from'
            } ${groupName}`,
          })
          router.refresh()
        } else {
          setNotice({ severity: 'error', message: result.error })
        }
      } catch (error) {
        setNotice({
          severity: 'error',
          message: error instanceof Error ? error.message : 'Failed to update assignment',
        })
      } finally {
        setPendingKey(null)
      }
    })
  }

  const columns: GridColDef<Row>[] = useMemo(
    () => [
      { field: 'name', headerName: 'Plan', flex: 1, minWidth: 220 },
      {
        field: 'description',
        headerName: 'Description',
        flex: 1,
        minWidth: 220,
        valueFormatter: value => (value ? value : '—'),
      },
      ...groups.map(
        (group): GridColDef<Row> => ({
          field: group.id,
          headerName: group.name,
          width: 130,
          type: 'boolean',
          filterable: false,
          sortingOrder: ['desc', 'asc', null],
          valueGetter: (_value, row) => assignmentSets[group.id]?.has(row.name) ?? false,
          renderCell: params => {
            const key = `${params.row.name}::${group.id}`
            const checked = assignmentSets[group.id]?.has(params.row.name) ?? false
            const disabled = pendingKey === key
            return (
              <Checkbox
                checked={checked}
                disabled={disabled}
                onChange={e =>
                  handleToggle(params.row.name, group.id, group.name, e.target.checked)
                }
              />
            )
          },
        })
      ),
    ],
    [groups, assignmentSets, pendingKey]
  )

  return (
    <Paper
      elevation={3}
      sx={{ width: '100%', maxWidth: 1200, overflow: 'hidden', borderRadius: 2 }}
    >
      <Toolbar disableGutters sx={{ px: 2 }}>
        <Typography variant="h6">Workout Plans</Typography>
      </Toolbar>
      <Box sx={{ height: '70vh' }}>
        <DataGrid
          rows={rows}
          columns={columns}
          disableRowSelectionOnClick
          initialState={{ sorting: { sortModel: [{ field: 'name', sort: 'asc' }] } }}
          loading={isPending}
        />
      </Box>
      <Snackbar open={notice !== null} autoHideDuration={4000} onClose={() => setNotice(null)}>
        {notice ? (
          <Alert severity={notice.severity} onClose={() => setNotice(null)}>
            {notice.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </Paper>
  )
}
