export const ProtocolTags = {
  TYPE: 1,
  MSG_ID: 2,
  SEQ: 3,
  PUSH_ID: 4,
  FLAGS: 5,
  PAYLOAD: 6,
  TS: 7,
  ACK_PUSH_ID: 8
} as const

export type ProtocolTag = (typeof ProtocolTags)[keyof typeof ProtocolTags]

