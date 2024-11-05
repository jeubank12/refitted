'use client'

import { useLogin } from 'src/lib/firebase/auth'

// async function getPosts() {
//   const res = await fetch('https://...')
//   const posts = await res.json()
//   return posts
// }

export default function Login() {
  // Fetch data directly in a Server Component
  // const recentPosts = await getPosts()
  // Forward fetched data to your Client Component
  const { doLogin } = useLogin()

  return <button onClick={doLogin}>Sign In with Google</button>
}
