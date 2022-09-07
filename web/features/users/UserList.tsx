import { useTestQuery } from 'store/aws'

const UserList = () => {
  const { data } = useTestQuery()
  return <p>{data}</p>
}

export default UserList
