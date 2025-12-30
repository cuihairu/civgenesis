export const ProtocolFlags = {
  ERROR: 0x01,
  COMPRESS: 0x02,
  ENCRYPT: 0x04,
  ACK_REQUIRED: 0x08
} as const

export type ProtocolFlagsValue = (typeof ProtocolFlags)[keyof typeof ProtocolFlags]

