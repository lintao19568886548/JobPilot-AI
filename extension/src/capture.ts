import type { VisibleFields, VisibleJobSnapshot } from './types.js'

export type CapturePayload = {
  platform: string
  pageUrl: string
  capturedAt: string
  userInitiated: true
  adapterVersion: string
  visibleFields: VisibleFields
  contentHash: string
}

export function buildCapturePayload(snapshot: VisibleJobSnapshot, visibleFields: VisibleFields): CapturePayload {
  return {
    platform: snapshot.platform,
    pageUrl: snapshot.pageUrl,
    capturedAt: snapshot.capturedAt,
    userInitiated: true,
    adapterVersion: snapshot.adapterVersion,
    visibleFields: {
      jobTitle: visibleFields.jobTitle.trim(),
      companyName: visibleFields.companyName.trim(),
      city: visibleFields.city?.trim() || undefined,
      salaryText: visibleFields.salaryText?.trim() || undefined,
      descriptionText: visibleFields.descriptionText.trim(),
      selectedText: visibleFields.selectedText?.trim() || undefined
    },
    contentHash: snapshot.contentHash
  }
}

export function validateCapturePayload(payload: CapturePayload): void {
  if (!payload.visibleFields.jobTitle || !payload.visibleFields.companyName || !payload.visibleFields.descriptionText) {
    throw new Error('请补全职位名称、公司与职位描述。')
  }
  if (!/^https?:\/\//.test(payload.pageUrl)) throw new Error('当前页面不是可读取的 HTTP(S) 页面。')
}
