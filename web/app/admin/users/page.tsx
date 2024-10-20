'use client'
import { useMemo } from 'react'

import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Paper from '@mui/material/Paper'
import { UserRecord } from 'firebase-admin/auth'

import Loading from 'features/components/loading'
import { useGetUsersQuery } from 'src/lib/aws/lambda'

const getUserCustomClaimTypes = (users: Array<UserRecord> | undefined) => {
  const allCustomClaims = users?.map(user => user.customClaims ?? {}) ?? []
  const customClaimsKeys = allCustomClaims
    .map(claims => Object.keys(claims))
    .reduce((agg, claims) => {
      return [...agg, ...claims]
    }, [])
  return [...new Set(customClaimsKeys)]
}

export default function UsersList() {
  const { data, isLoading } = useGetUsersQuery()
  const claimTypes = useMemo(
    () => getUserCustomClaimTypes(data?.users),
    [data?.users]
  )
  if (isLoading || !data) return <Loading />
  else if (!data?.users.length) return <div>empty</div>
  else
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
              {data.users.map(user => (
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
