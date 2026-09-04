export type CaptureStatus = 'READY' | 'NEEDS_REVIEW'

export type VisibleFields = {
  jobTitle: string
  companyName: string
  city?: string
  salaryText?: string
  descriptionText: string
  selectedText?: string
}

export type VisibleJobSnapshot = {
  adapterId: 'FIXTURE_V1' | 'GENERIC_VISIBLE_V1' | 'MANUAL_SELECTION_V1'
  adapterVersion: string
  status: CaptureStatus
  platform: string
  pageUrl: string
  capturedAt: string
  visibleFields: VisibleFields
  contentHash: string
  warnings: string[]
}

export type ExtensionTokens = {
  accessToken: string
  refreshToken: string
  expiresIn: number
  device: { id: string; deviceName: string; status: string }
  scopes: string[]
}
