import Loading from 'features/components/loading'
import { useGetUsersQuery } from 'store/aws/lambda/lambdaEndpoints'

const UserList = () => {
  const { data: users, isLoading } = useGetUsersQuery()
  return (
    <div>
      {isLoading ? (
        <Loading />
      ) : (
        users?.users?.map(user => <p key={user.uid}>{user.email}</p>)
      )}
    </div>
  )
}

export default UserList
