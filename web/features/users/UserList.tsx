import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Paper from '@mui/material/Paper'

import { useGetUsersQuery } from 'store/aws/lambda/lambdaEndpoints'
import Loading from 'features/components/loading'

const UserList = () => {
  const { data, isLoading } = useGetUsersQuery()
  if (isLoading) return <Loading />
  else if (!data) return <div>empty</div>
  else
    return (
      <TableContainer component={Paper}>
        <Table sx={{ minWidth: 650 }} aria-label="simple table">
          <TableHead>
            <TableRow>
              <TableCell>Email</TableCell>
              <TableCell align="right">Custom Claims</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.users.map(user => (
              <TableRow
                key={user.uid}
                sx={{ '&:last-child td, &:last-child th': { border: 0 } }}
              >
                <TableCell component="th" scope="row">
                  {user.email}
                </TableCell>
                <TableCell align="right">
                  {JSON.stringify(user.customClaims)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    )
}

export default UserList
