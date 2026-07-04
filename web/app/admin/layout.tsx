'use client'
import { ReactNode } from 'react'

import { AppRouterCacheProvider } from '@mui/material-nextjs/v15-appRouter'
import { ThemeProvider, createTheme } from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'

import styles from 'styles/Home.module.css'

const darkTheme = createTheme({
  palette: {
    mode: 'dark',
    background: {
      default: '#121212',
      paper: '#1e1e1e',
    },
    primary: {
      main: '#90caf9',
    },
    error: {
      main: '#f44336',
    },
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          fontSize: '0.95rem',
          fontWeight: 600,
          padding: '10px 24px',
          textTransform: 'none',
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        head: {
          backgroundColor: '#2d2d2d',
          fontWeight: 600,
          fontSize: '0.875rem',
        },
        body: {
          fontSize: '0.875rem',
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
        },
      },
    },
  },
})

export default function AdminLayout({
  children,
  logout,
}: {
  children: ReactNode
  logout: ReactNode
}) {
  return (
    <AppRouterCacheProvider>
      <ThemeProvider theme={darkTheme}>
        <CssBaseline />
        <div className={styles.container}>
          <header>{logout}</header>

          <main className={styles.main}>{children}</main>
        </div>
      </ThemeProvider>
    </AppRouterCacheProvider>
  )
}
