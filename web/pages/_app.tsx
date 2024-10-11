import 'styles/globals.css'
import type { AppProps } from 'next/app'
import { Provider } from 'react-redux'

function MyApp({ Component, ...rest }: AppProps) {
  return <Component />
}

export default MyApp
