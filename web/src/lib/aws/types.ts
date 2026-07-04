export type SetClaimResult =
  | { ok: true; claims: Record<string, unknown> }
  | { ok: false; error: string }

export type UpdateGroupResult =
  | { status: 'ok'; dynamo: string[]; iam: string[] }
  | { status: 'dynamo-failed'; error: string }
  | { status: 'iam-failed-rolled-back'; error: string }
  | { status: 'iam-failed-rollback-failed'; error: string; rollbackError: string }
  | { status: 'unauthorized' | 'app-check-failed' | 'unknown-group'; error: string }

export type DeleteUsersResult =
  | { ok: true; successCount: number; failureCount: number; errors: string[] }
  | { ok: false; error: string }
