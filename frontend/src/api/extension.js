import { http } from './http.js'

export const createExtensionPairingCode = () => http.post('/v1/extension/pairing-codes')
export const listExtensionDevices = () => http.get('/v1/extension/devices')
export const revokeExtensionDevice = (id) => http.delete(`/v1/extension/devices/${id}`)
