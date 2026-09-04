import { createHmac, timingSafeEqual } from 'node:crypto'

export function verifyTaskToken(token: string, taskId: string, queueApprovalId: string, policyMode: string,
  serviceToken: string, nowEpochSeconds = Math.floor(Date.now() / 1000)): boolean {
  if (!serviceToken || serviceToken.length < 24 || !token.startsWith('jpt_')) return false
  const [encodedClaims, encodedSignature, extra] = token.slice(4).split('.')
  if (!encodedClaims || !encodedSignature || extra) return false
  try {
    const supplied = Buffer.from(encodedSignature, 'base64url')
    const expected = createHmac('sha256', serviceToken).update(encodedClaims).digest()
    if (supplied.length !== expected.length || !timingSafeEqual(supplied, expected)) return false
    const [claimedTaskId, expiresAt, claimedQueue, claimedPolicy, surplus] = Buffer.from(encodedClaims, 'base64url').toString('utf8').split('|')
    return !surplus && claimedTaskId === taskId && claimedQueue === queueApprovalId && claimedPolicy === policyMode
      && Number.isSafeInteger(Number(expiresAt)) && Number(expiresAt) >= nowEpochSeconds
  } catch {
    return false
  }
}
