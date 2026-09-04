import { z } from 'zod'

export const allowedSelectors = ['#jp-name', '#jp-email', '#jp-phone', '#jp-summary'] as const

export const prepareRequestSchema = z.object({
  schemaVersion: z.literal('assist-prepare-request-v1'),
  taskId: z.string().min(1).max(26),
  taskToken: z.string().min(20).max(500),
  targetUrl: z.url().max(1000),
  queueApprovalId: z.string().min(1).max(26),
  policyMode: z.literal('ASSIST_ALLOWED'),
  finalConfirmationRequired: z.literal(true),
  fields: z.array(z.object({
    selector: z.enum(allowedSelectors),
    value: z.string().max(4000)
  }).strict()).max(4)
}).strict()

export type PrepareRequest = z.infer<typeof prepareRequestSchema>

export type PrepareStep = {
  type: 'OPEN' | 'CHECK' | 'FILL' | 'HANDOFF' | 'BLOCKED'
  status: 'SUCCEEDED' | 'BLOCKED'
  selector?: string
  detail: string
}

export type PrepareResponse = {
  schemaVersion: 'assist-prepare-response-v1'
  taskId: string
  status: 'PREPARED' | 'BLOCKED'
  externallySubmitted: false
  applicationCreated: false
  finalConfirmationRequired: true
  submitCount: 0
  blockedReason: string | null
  steps: PrepareStep[]
}
