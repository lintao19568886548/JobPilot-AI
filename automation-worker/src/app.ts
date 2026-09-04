import express from 'express'
import { fileURLToPath } from 'node:url'
import { loadConfig, type WorkerConfig } from './config.js'
import { prepare } from './prepare.js'
import { prepareRequestSchema, type PrepareResponse } from './schema.js'
import { verifyTaskToken } from './task-token.js'

export function createApp(config: WorkerConfig = loadConfig()) {
  const app = express()
  const completed = new Map<string, PrepareResponse>()
  app.use(express.json({ limit: '64kb' }))
  app.use('/fixtures', express.static(fileURLToPath(new URL('../fixtures', import.meta.url))))
  app.get('/internal/v1/health', (_request, response) => response.json({
    status: 'UP', enabled: config.enabled, safetyMode: 'FILL_WITHOUT_SUBMIT'
  }))
  app.post('/internal/v1/assist/prepare', async (request, response) => {
    if (!config.serviceToken || request.header('authorization') !== `Bearer ${config.serviceToken}`) {
      response.status(401).json({ code: 'UNAUTHORIZED', message: 'Service authentication required' }); return
    }
    const parsed = prepareRequestSchema.safeParse(request.body)
    if (!parsed.success) {
      response.status(400).json({ code: 'INVALID_REQUEST', message: 'Strict request validation failed' }); return
    }
    if (!verifyTaskToken(parsed.data.taskToken, parsed.data.taskId, parsed.data.queueApprovalId,
      parsed.data.policyMode, config.serviceToken)) {
      response.status(401).json({ code: 'INVALID_TASK_TOKEN', message: 'Task token is invalid or expired' }); return
    }
    const previous = completed.get(parsed.data.taskId)
    if (previous) { response.json(previous); return }
    try {
      const result = await prepare(parsed.data, config)
      completed.set(parsed.data.taskId, result)
      response.json(result)
    } catch (error) {
      const code = error instanceof Error ? error.message : 'PREPARE_FAILED'
      response.status(code === 'WORKER_DISABLED' ? 503 : 422).json({ code, message: 'Assist preparation was not executed' })
    }
  })
  return app
}
