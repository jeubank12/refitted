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
  App Check debug token locally (already set in `.env.development`, which
  `npm run dev` loads automatically). This makes the App Check SDK generate
  a random debug token and print it to the browser console on first load
  instead of doing a real reCAPTCHA v3 attestation — but a freshly generated
  token isn't trusted by Firebase until you register it. If login/mutations
  fail with a 403 from `exchangeToken`/`exchangeDebugToken`, look for a
  `App Check debug token: <uuid>` line in the browser console and add it in
  **Firebase Console → App Check → Apps → (this web app) → Manage debug
  tokens**.

Runtime, server-only secrets:

- `FIREBASE_SERVICE_ACCOUNT_B64` — base64 of the Firebase Admin SDK
  service account JSON (`base64 -w0 firebase.json`). Read lazily in
  `src/lib/firebase/admin.ts`, so `npm run build` succeeds without it set.
- `REFITTED_GROUPS_B64` — base64 of a JSON object mapping each workout
  access group to its DynamoDB/IAM identifiers:
  ```json
  { "Paid1": { "id": "<group-uuid>", "policyArn": "arn:aws:iam::<acct>:policy/DynamoDb-Refitted.Dev01-Paid1" } }
  ```
  Read lazily in `src/lib/aws/groups.ts`. These are the user's IP + infra
  identifiers, never committed. Used only by `updateGroupWorkouts` (see
  below) and the local `test:groups` script.

Ask a maintainer for the real values. In CI, each of these is set from a
same-named GitHub secret (see `.github/workflows/build.yml`).

## Architecture

Server components and server actions run in trusted server code (deployed
as a Lambda container via Lambda Web Adapter, built by CDK from another
branch). The browser never talks to AWS directly. `proxy.ts` runs on the
Node.js runtime and verifies the session cookie with `firebase-admin` for
routing; every server action independently re-verifies the session via
`getAuthenticatedAuth()` as defense in depth.

## Admin execution security

Two server actions perform privileged operations that used to be separate
AWS Lambdas invoked from the browser (`admin/`, now removed — see git
history for the originals):

- `setUserClaim` (`src/lib/firebase/actions/claims.ts`) — sets a custom
  claim (typically `group`) on a Firebase user.
- `updateGroupWorkouts` (`src/lib/aws/actions/groups.ts`) — updates the
  DynamoDB workout list for a group (`src/lib/aws/dynamo.ts`) and the IAM
  policy that enforces it (`src/lib/aws/iam.ts`).

**Authorization chain**, in order, for every call to either action:
1. `proxy.ts` — routing/UX check that a session cookie exists before the
   request ever reaches a page or action (not itself a security boundary).
2. **App Check validation** (`validateAppCheck`, mutations only) — the
   caller must present a fresh reCAPTCHA v3 attestation token proving the
   request originates from the real app in a real browser. This is what
   stops a stolen httpOnly session cookie from being replayed via
   curl/script: the attacker would also need to drive the actual app UI to
   mint a token. Read-only `listAllUsers` does not require this.
3. `getAuthenticatedAuth()` — verifies the session cookie
   (`verifySessionCookie` with `checkRevoked: true`) and requires the
   `admin` custom claim.
4. The operation itself.

**The browser never holds AWS credentials.** All `@aws-sdk/*` calls run in
server-only code, authenticating with the process's ambient credentials
(a dev AWS profile locally; a locked-down execution role on the deployed
Lambda container). This mirrors how the original lambdas worked — the
Cognito role the admin's browser held only ever granted
`lambda:InvokeFunction`, never direct IAM/DynamoDB write; the elevated
permissions lived on the lambda's own execution role.

**Least-privilege runtime policy** (for the CDK role provisioning the
deployed execution role — not yet implemented, see `infra/lib/auth-stack.ts`
for the existing `IAM-Refitted-EditGroups` policy and table ARN this
should be scoped to):
- `iam:GetPolicyVersion`, `iam:ListPolicyVersions`, `iam:CreatePolicyVersion`,
  `iam:DeletePolicyVersion` — scoped to the group policy ARNs only
  (`DynamoDb-Refitted.Dev01-{Paid1,Free,Anon}`).
- `dynamodb:GetItem`, `dynamodb:PutItem` — scoped to the `refitted.dev01`
  table only.

**`updateGroupWorkouts` failure handling** is sequential with a
compensating rollback, not independent halves: DynamoDB is updated first
(the easy-to-reverse half); if that fails, IAM is never touched. If the
IAM update then fails, DynamoDB is rolled back to its pre-update snapshot.
IAM policy-version history is far harder to undo than a DynamoDB
`PutItem`, so it always runs last and is the only half that's ever left
in its new state on partial failure. The result's `status` field
distinguishes a clean rollback (`iam-failed-rolled-back`) from the rare
case where the rollback write itself also fails
(`iam-failed-rollback-failed` — needs manual reconciliation).

**No UI calls these actions yet** (a follow-up). To validate them locally,
see `npm run test:groups` below.

## Testing the group actions without UI

```bash
npm run test:groups -- <GroupName>            # dry run: reads current state only
npm run test:groups -- <GroupName> --apply --add Foo --remove Bar
```

Requires `REFITTED_GROUPS_B64` and AWS credentials (a dev profile) in your
environment. `--apply` mutates real DynamoDB rows and real IAM policy
versions (IAM keeps a maximum of 5 versions per policy) — do not run it
against a group whose current state you haven't already inspected with a
dry run.
