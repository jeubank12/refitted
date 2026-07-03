Refitted's admin web app: a Next.js App Router site using React server
components/actions for admin user management, backed by Firebase Auth
(session cookies) and Firebase App Check.

## Getting Started

```bash
npm ci
npm run dev
```

Open [http://localhost:3000/admin](http://localhost:3000/admin) with your browser.

## Local configuration

No config values are committed to this repo — public or not. Create
`.env.local` (gitignored) with the following:

Build-time, inlined into client code by Next.js (`NEXT_PUBLIC_*` vars are
public identifiers — Firebase web config and the reCAPTCHA v3 site key —
but are still kept out of the repo per policy):

- `NEXT_PUBLIC_FIREBASE_API_KEY`
- `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN`
- `NEXT_PUBLIC_FIREBASE_DATABASE_URL`
- `NEXT_PUBLIC_FIREBASE_PROJECT_ID`
- `NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET`
- `NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID`
- `NEXT_PUBLIC_FIREBASE_APP_ID`
- `NEXT_PUBLIC_FIREBASE_MEASUREMENT_ID`
- `NEXT_PUBLIC_RECAPTCHA_SITE_KEY`
- `NEXT_PUBLIC_DEV_TOOLS_ENABLED` — set to `true` to enable the Firebase
  App Check debug token locally.

Runtime, server-only secret:

- `FIREBASE_SERVICE_ACCOUNT_B64` — base64 of the Firebase Admin SDK
  service account JSON (`base64 -w0 firebase.json`). Read lazily in
  `src/lib/firebase/admin.ts`, so `npm run build` succeeds without it set.

Ask a maintainer for the real values. In CI, each of these is set from a
same-named GitHub secret (see `.github/workflows/build.yml`).

## Architecture

Server components and server actions run in trusted server code (deployed
as a Lambda container via Lambda Web Adapter, built by CDK from another
branch). The browser never talks to AWS directly. `proxy.ts` runs on the
Node.js runtime and verifies the session cookie with `firebase-admin` for
routing; every server action independently re-verifies the session via
`getAuthenticatedAuth()` as defense in depth.
