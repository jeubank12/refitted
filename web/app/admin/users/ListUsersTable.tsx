'use client'
import { useMemo, useState, useTransition } from 'react'

import { useRouter } from 'next/navigation'

import {
  DataGrid,
  GridColDef,
  GridFilterModel,
  GridRowSelectionModel,
} from '@mui/x-data-grid'
import Paper from '@mui/material/Paper'
import Box from '@mui/material/Box'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import FormControlLabel from '@mui/material/FormControlLabel'
import Switch from '@mui/material/Switch'
import Select from '@mui/material/Select'
import MenuItem from '@mui/material/MenuItem'
import FormControl from '@mui/material/FormControl'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogActions from '@mui/material/DialogActions'
import Snackbar from '@mui/material/Snackbar'
import Alert from '@mui/material/Alert'

import { useAppCheckToken } from 'src/lib/firebase/appCheck'
import { setUserClaim } from 'src/lib/firebase/actions/claims'
import { deleteUsers } from 'src/lib/firebase/actions/users'

/**
 * Serialized user record from Firebase Admin SDK
 * This is a plain object version of UserRecord (result of toJSON())
 */
type SerializedUserRecord = {
  uid: string
  email?: string
  emailVerified?: boolean
  customClaims?: Record<string, unknown>
  createdAt?: number | null
  lastSignInAt?: number | null
}

type Row = SerializedUserRecord & { id: string }

const EMPTY_SELECTION: GridRowSelectionModel = { type: 'include', ids: new Set() }
const NO_FILTER: GridFilterModel = { items: [] }
const ANONYMOUS_FILTER: GridFilterModel = {
  items: [{ field: 'email', operator: 'isEmpty' }],
}

// The `group` claim gets its own dedicated column (with an assignment
// dropdown), so it's excluded from the generic claim-column list.
const getUserCustomClaimTypes = (users: Array<SerializedUserRecord>) => {
  const allCustomClaims = users.map(user => user.customClaims ?? {})
  const customClaimsKeys = allCustomClaims
    .map(claims => Object.keys(claims))
    .reduce((agg, claims) => [...agg, ...claims], [] as string[])
  return [...new Set(customClaimsKeys)].filter(key => key !== 'group')
}

function formatDate(value?: number | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

export default function UsersList({
  users,
  groups,
  groupNamesById,
}: {
  users: Array<SerializedUserRecord>
  groups: Array<{ name: string; id: string }>
  groupNamesById: Record<string, string>
}) {
  const router = useRouter()
  const { getAppCheckToken } = useAppCheckToken()
  const [isPending, startTransition] = useTransition()
  const [rowSelectionModel, setRowSelectionModel] =
    useState<GridRowSelectionModel>(EMPTY_SELECTION)
  const [anonymousOnly, setAnonymousOnly] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [notice, setNotice] = useState<{
    severity: 'success' | 'error'
    message: string
  } | null>(null)
  const [assigningUid, setAssigningUid] = useState<string | null>(null)

  const otherClaimTypes = useMemo(() => getUserCustomClaimTypes(users), [users])

  const rows = useMemo<Row[]>(
    () => users.map(user => ({ id: user.uid, ...user })),
    [users]
  )

  const selectedUids = useMemo(() => {
    if (rowSelectionModel.type === 'include') {
      return [...rowSelectionModel.ids].map(String)
    }
    // 'exclude' mode isn't reachable without server-side row count, but
    // handle it defensively rather than assume 'include'.
    const excluded = new Set([...rowSelectionModel.ids].map(String))
    return rows.map(row => row.id).filter(id => !excluded.has(id))
  }, [rowSelectionModel, rows])

  const handleAssignGroup = (
    uid: string,
    email: string | undefined,
    groupId: string
  ) => {
    if (!email) return
    setAssigningUid(uid)
    startTransition(async () => {
      try {
        const token = await getAppCheckToken()
        const result = await setUserClaim(email, 'group', groupId, token)
        if (result.ok) {
          setNotice({ severity: 'success', message: 'Group updated' })
          router.refresh()
        } else {
          setNotice({ severity: 'error', message: result.error })
        }
      } catch (error) {
        setNotice({
          severity: 'error',
          message: error instanceof Error ? error.message : 'Failed to update group',
        })
      } finally {
        setAssigningUid(null)
      }
    })
  }

  const handleDeleteSelected = () => {
    setConfirmOpen(false)
    startTransition(async () => {
      try {
        const token = await getAppCheckToken()
        const result = await deleteUsers(selectedUids, token)
        if (result.ok) {
          setNotice({
            severity: result.failureCount ? 'error' : 'success',
            message: `Deleted ${result.successCount} user(s)${
              result.failureCount ? `, ${result.failureCount} failed` : ''
            }`,
          })
          setRowSelectionModel(EMPTY_SELECTION)
          router.refresh()
        } else {
          setNotice({ severity: 'error', message: result.error })
        }
      } catch (error) {
        setNotice({
          severity: 'error',
          message: error instanceof Error ? error.message : 'Failed to delete users',
        })
      }
    })
  }

  const columns: GridColDef<Row>[] = useMemo(
    () => [
      {
        field: 'email',
        headerName: 'Email',
        flex: 1,
        minWidth: 220,
        // Keep the raw (possibly empty) email as the sort/filter value so the
        // "Anonymous only" isEmpty filter can match it; the '(anonymous)'
        // fallback is display-only, applied in valueFormatter.
        valueGetter: (_value, row) => row.email ?? '',
        valueFormatter: value => (value ? value : '(anonymous)'),
      },
      {
        field: 'group',
        headerName: 'Group',
        width: 220,
        valueGetter: (_value, row) => {
          const groupId = row.customClaims?.group as string | undefined
          if (!groupId) return ''
          return groupNamesById[groupId] ?? groupId
        },
        renderCell: params => {
          const { row } = params
          const groupId = (row.customClaims?.group as string | undefined) ?? ''
          const disabled = !row.emailVerified || assigningUid === row.uid
          return (
            <FormControl size="small" fullWidth disabled={disabled}>
              <Select
                value={groupId}
                displayEmpty
                onChange={e => handleAssignGroup(row.uid, row.email, e.target.value)}
              >
                <MenuItem value="">
                  <em>None</em>
                </MenuItem>
                {groups.map(group => (
                  <MenuItem key={group.id} value={group.id}>
                    {group.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )
        },
      },
      ...otherClaimTypes.map(
        (claimName): GridColDef<Row> => ({
          field: claimName,
          headerName: claimName,
          width: 150,
          valueGetter: (_value, row) => JSON.stringify(row.customClaims?.[claimName]),
        })
      ),
      {
        field: 'createdAt',
        headerName: 'Created',
        width: 190,
        type: 'number',
        valueFormatter: value => formatDate(value as number | null),
      },
      {
        field: 'lastSignInAt',
        headerName: 'Last sign-in',
        width: 190,
        type: 'number',
        valueFormatter: value => formatDate(value as number | null),
      },
    ],
    [otherClaimTypes, groups, groupNamesById, assigningUid]
  )

  return (
    <Paper
      elevation={3}
      sx={{ width: '100%', maxWidth: 1200, overflow: 'hidden', borderRadius: 2 }}
    >
      <Toolbar disableGutters sx={{ px: 2, gap: 2, flexWrap: 'wrap' }}>
        <FormControlLabel
          control={
            <Switch
              checked={anonymousOnly}
              onChange={e => setAnonymousOnly(e.target.checked)}
            />
          }
          label="Anonymous only"
        />
        <Box sx={{ flex: 1 }} />
        {selectedUids.length > 0 && (
          <>
            <Typography variant="body2">{selectedUids.length} selected</Typography>
            <Button color="error" variant="outlined" onClick={() => setConfirmOpen(true)}>
              Delete
            </Button>
          </>
        )}
      </Toolbar>
      <Box sx={{ height: '70vh' }}>
        <DataGrid
          rows={rows}
          columns={columns}
          checkboxSelection
          disableRowSelectionOnClick
          rowSelectionModel={rowSelectionModel}
          onRowSelectionModelChange={setRowSelectionModel}
          filterModel={anonymousOnly ? ANONYMOUS_FILTER : NO_FILTER}
          initialState={{
            sorting: { sortModel: [{ field: 'createdAt', sort: 'desc' }] },
          }}
          loading={isPending}
        />
      </Box>
      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)}>
        <DialogTitle>Delete {selectedUids.length} user(s)?</DialogTitle>
        <DialogContent>
          <DialogContentText>This cannot be undone.</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)}>Cancel</Button>
          <Button color="error" onClick={handleDeleteSelected}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
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
