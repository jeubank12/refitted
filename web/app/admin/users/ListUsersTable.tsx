'use client'
import { useMemo } from 'react'

import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Paper from '@mui/material/Paper'

/**
 * Serialized user record from Firebase Admin SDK
 * This is a plain object version of UserRecord (result of toJSON())
 */
type SerializedUserRecord = {
  uid: string
  email?: string
  customClaims?: Record<string, unknown>
  // Add other fields as needed
}

const getUserCustomClaimTypes = (
  users: Array<SerializedUserRecord> | undefined
) => {
  const allCustomClaims = users?.map(user => user.customClaims ?? {}) ?? []
  const customClaimsKeys = allCustomClaims
    .map(claims => Object.keys(claims))
    .reduce((agg, claims) => {
      return [...agg, ...claims]
    }, [])
  return [...new Set(customClaimsKeys)]
}

export default function UsersList({
  users,
}: {
  users: Array<SerializedUserRecord>
}) {
  const claimTypes = useMemo(() => getUserCustomClaimTypes(users), [users])

  return (
    <Paper sx={{ width: '100%', maxWidth: 900, overflow: 'hidden' }}>
      <TableContainer sx={{ maxHeight: '100%' }}>
        <Table stickyHeader sx={{ minWidth: 650 }} aria-label="sticky table">
          <TableHead>
            <TableRow>
              <TableCell>Email</TableCell>
              {claimTypes.map(claimName => (
                <TableCell align="right" key={claimName}>
                  {claimName}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {users.map(user => (
              <TableRow
                key={user.uid}
                sx={{ '&:last-child td, &:last-child th': { border: 0 } }}
              >
                <TableCell component="th" scope="row">
                  {user.email || '(anonymous)'}
                </TableCell>
                {claimTypes.map(claimName => (
                  <TableCell align="right" key={claimName}>
                    {JSON.stringify(user.customClaims?.[claimName])}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  )
}
