import { FrameType } from './frameType.js'

export type Frame = Readonly<{
  type: FrameType
  msgId?: number
  seq?: number
  pushId?: number
  flags?: number
  ts?: number
  ackPushId?: number
  payload?: Uint8Array
}>

export function isFrameType(value: number): value is FrameType {
  return (
    value === FrameType.REQ ||
    value === FrameType.RESP ||
    value === FrameType.PUSH ||
    value === FrameType.ACK ||
    value === FrameType.PING ||
    value === FrameType.PONG
  )
}

