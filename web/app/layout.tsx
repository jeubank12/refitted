import 'styles/globals.css'
import { Metadata } from 'next'

export const metadata: Metadata = {
  title: 'Litus Animae',
  description: 'site for Litus Animae',
}

export default function RootLayout({
  // Layouts must accept a children prop.
  // This will be populated with nested layouts or pages
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <head>
        <link rel="icon" type="image/ico" href="/public/favicon.ico"></link>
      </head>
      <body>{children}</body>
    </html>
  )
}
