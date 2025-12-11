import Login from './Login'

export default async function Page() {
  // Redirect logic now handled by proxy middleware
  // If user has valid JWT, proxy redirects to /admin/users
  // If not, this page renders the login form
  return <Login />
}
