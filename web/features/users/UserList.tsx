import { useSelector } from 'react-redux'

import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Paper from '@mui/material/Paper'

import { useGetUsersQuery } from 'store/aws/lambda/lambdaEndpoints'
import Loading from 'features/components/loading'
import { getUserCustomClaimTypes } from 'store/aws/lambda/lambdaSelectors'

const UserList = () => {
  const { data, isLoading } = useGetUsersQuery()
  const claimTypes = useSelector(getUserCustomClaimTypes)
  if (isLoading) return <Loading />
  else if (!data) return <div>empty</div>
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

export default UserList
