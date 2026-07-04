export interface GroupConfig {
  id: string
  policyArn: string
}

let cachedConfig: Record<string, GroupConfig> | undefined

function parseGroupsConfig(): Record<string, GroupConfig> {
  const b64 = process.env.REFITTED_GROUPS_B64
  if (!b64) throw new Error('REFITTED_GROUPS_B64 is not set')

  const parsed = JSON.parse(Buffer.from(b64, 'base64').toString('utf8'))
  for (const [name, config] of Object.entries(parsed)) {
    const { id, policyArn } = (config ?? {}) as Partial<GroupConfig>
    if (typeof id !== 'string' || typeof policyArn !== 'string') {
      throw new Error(`REFITTED_GROUPS_B64 entry "${name}" is missing id/policyArn`)
    }
  }
  return parsed as Record<string, GroupConfig>
}

export function getGroupsConfig(): Record<string, GroupConfig> {
  if (!cachedConfig) {
    cachedConfig = parseGroupsConfig()
  }
  return cachedConfig
}

export function getGroupNames(): string[] {
  return Object.keys(getGroupsConfig())
}

export function getGroupConfig(group: string): GroupConfig {
  const config = getGroupsConfig()[group]
  if (!config) throw new Error(`Unknown group: ${group}`)
  return config
}
