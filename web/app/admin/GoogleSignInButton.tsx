import { styled } from '@mui/material/styles'

// Port of Google's official dark "Sign in with Google" button markup/CSS
// (https://developers.google.com/identity/branding-guidelines), scoped to a
// styled component instead of global classnames. The admin layout is always
// dark-themed, so only the dark button variant is implemented.
const StyledButton = styled('button')({
  WebkitAppearance: 'none',
  backgroundColor: '#131314',
  backgroundImage: 'none',
  border: '1px solid #8e918f',
  borderRadius: 4,
  boxSizing: 'border-box',
  color: '#e3e3e3',
  cursor: 'pointer',
  fontFamily: "'Roboto', arial, sans-serif",
  fontSize: 14,
  height: 40,
  letterSpacing: '0.25px',
  outline: 'none',
  overflow: 'hidden',
  padding: '0 12px',
  position: 'relative',
  textAlign: 'center',
  transition: 'background-color .218s, border-color .218s, box-shadow .218s',
  userSelect: 'none',
  verticalAlign: 'middle',
  whiteSpace: 'nowrap',
  width: 'auto',
  maxWidth: 400,
  minWidth: 'min-content',

  '& .gsi-icon': {
    height: 20,
    marginRight: 10,
    minWidth: 20,
    width: 20,
  },
  '& .gsi-content-wrapper': {
    alignItems: 'center',
    display: 'flex',
    flexDirection: 'row',
    flexWrap: 'nowrap',
    height: '100%',
    justifyContent: 'space-between',
    position: 'relative',
    width: '100%',
  },
  '& .gsi-contents': {
    flexGrow: 1,
    fontFamily: "'Roboto', arial, sans-serif",
    fontWeight: 500,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    verticalAlign: 'top',
  },
  '& .gsi-state': {
    transition: 'opacity .218s',
    position: 'absolute',
    inset: 0,
    opacity: 0,
  },

  '&:disabled': {
    cursor: 'default',
    backgroundColor: '#13131461',
    borderColor: '#8e918f1f',
  },
  '&:disabled .gsi-state': {
    backgroundColor: '#e3e3e31f',
  },
  '&:disabled .gsi-contents, &:disabled .gsi-icon': {
    opacity: 0.38,
  },
  '&:not(:disabled):active .gsi-state, &:not(:disabled):focus .gsi-state': {
    backgroundColor: '#fff',
    opacity: 0.12,
  },
  '&:not(:disabled):hover': {
    boxShadow: '0 1px 2px 0 rgba(60, 64, 67, .30), 0 1px 3px 1px rgba(60, 64, 67, .15)',
  },
  '&:not(:disabled):hover .gsi-state': {
    backgroundColor: '#fff',
    opacity: 0.08,
  },
})

function GoogleLogo() {
  return (
    <svg viewBox="0 0 48 48" style={{ display: 'block' }} aria-hidden="true" focusable="false">
      <path
        fill="#EA4335"
        d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
      />
      <path
        fill="#4285F4"
        d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
      />
      <path
        fill="#FBBC05"
        d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
      />
      <path
        fill="#34A853"
        d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
      />
    </svg>
  )
}

export default function GoogleSignInButton({ onClick }: { onClick: () => void }) {
  return (
    <StyledButton type="button" onClick={onClick}>
      <span className="gsi-state" />
      <span className="gsi-content-wrapper">
        <span className="gsi-icon">
          <GoogleLogo />
        </span>
        <span className="gsi-contents">Sign in with Google</span>
      </span>
    </StyledButton>
  )
}
