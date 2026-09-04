export type WorkerConfig = {
  enabled: boolean
  port: number
  serviceToken: string
  allowedHosts: Set<string>
  headless: boolean
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): WorkerConfig {
  return {
    enabled: env.AUTOMATION_WORKER_ENABLED === 'true',
    port: Number(env.AUTOMATION_WORKER_PORT || 8020),
    serviceToken: env.AUTOMATION_WORKER_TOKEN || '',
    allowedHosts: new Set((env.AUTOMATION_ALLOWED_HOSTS || '127.0.0.1,localhost')
      .split(',').map((item) => item.trim().toLowerCase()).filter(Boolean)),
    headless: env.AUTOMATION_HEADLESS !== 'false'
  }
}

export function requireAllowedUrl(rawUrl: string, config: WorkerConfig): URL {
  const value = new URL(rawUrl)
  if (!['http:', 'https:'].includes(value.protocol) || !config.allowedHosts.has(value.hostname.toLowerCase())) {
    throw new Error('TARGET_NOT_ALLOWED')
  }
  return value
}
