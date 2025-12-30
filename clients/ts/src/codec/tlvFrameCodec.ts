import { decodeUVarint, encodeUVarint } from './varint.js'
import { Frame, isFrameType } from '../protocol/frame.js'
import { FrameType } from '../protocol/frameType.js'
import { ProtocolTags } from '../protocol/protocolTags.js'

export type TlvFrameCodecLimits = Readonly<{
  maxFrameBytes: number
  maxTlvCount: number
  maxTlvValueBytes: number
}>

const DEFAULT_LIMITS: TlvFrameCodecLimits = {
  maxFrameBytes: 1024 * 1024,
  maxTlvCount: 128,
  maxTlvValueBytes: 1024 * 1024
}

class Writer {
  #buf: Uint8Array
  #pos = 0

  constructor(initialCapacity = 256) {
    this.#buf = new Uint8Array(initialCapacity)
  }

  ensure(extraBytes: number) {
    const need = this.#pos + extraBytes
    if (need <= this.#buf.length) return
    let next = this.#buf.length
    while (next < need) next *= 2
    const b = new Uint8Array(next)
    b.set(this.#buf, 0)
    this.#buf = b
  }

  writeBytes(bytes: Uint8Array) {
    this.ensure(bytes.length)
    this.#buf.set(bytes, this.#pos)
    this.#pos += bytes.length
  }

  toUint8Array(): Uint8Array {
    return this.#buf.subarray(0, this.#pos)
  }
}

function encodeTlv(writer: Writer, tag: number, value: Uint8Array) {
  writer.writeBytes(encodeUVarint(tag))
  writer.writeBytes(encodeUVarint(value.length))
  writer.writeBytes(value)
}

function encodeVarintField(writer: Writer, tag: number, value: number) {
  if (!Number.isSafeInteger(value) || value < 0) throw new Error(`Invalid uvarint field value: ${value}`)
  const v = encodeUVarint(value)
  encodeTlv(writer, tag, v)
}

export function encodeFrame(frame: Frame, limits: Partial<TlvFrameCodecLimits> = {}): Uint8Array {
  const l = { ...DEFAULT_LIMITS, ...limits }
  const w = new Writer()

  encodeVarintField(w, ProtocolTags.TYPE, frame.type)

  if (frame.flags !== undefined && frame.flags !== 0) encodeVarintField(w, ProtocolTags.FLAGS, frame.flags)
  if (frame.msgId !== undefined && frame.msgId !== 0) encodeVarintField(w, ProtocolTags.MSG_ID, frame.msgId)
  if (frame.seq !== undefined && frame.seq !== 0) encodeVarintField(w, ProtocolTags.SEQ, frame.seq)
  if (frame.pushId !== undefined && frame.pushId !== 0) encodeVarintField(w, ProtocolTags.PUSH_ID, frame.pushId)
  if (frame.ts !== undefined && frame.ts !== 0) encodeVarintField(w, ProtocolTags.TS, frame.ts)
  if (frame.ackPushId !== undefined && frame.ackPushId !== 0) encodeVarintField(w, ProtocolTags.ACK_PUSH_ID, frame.ackPushId)
  if (frame.payload !== undefined && frame.payload.length !== 0) encodeTlv(w, ProtocolTags.PAYLOAD, frame.payload)

  const out = w.toUint8Array()
  if (out.length > l.maxFrameBytes) throw new Error(`Frame too large: ${out.length} > ${l.maxFrameBytes}`)
  return out
}

export function decodeFrame(bytes: Uint8Array, limits: Partial<TlvFrameCodecLimits> = {}): Frame {
  const l = { ...DEFAULT_LIMITS, ...limits }
  if (bytes.length > l.maxFrameBytes) throw new Error(`Frame too large: ${bytes.length} > ${l.maxFrameBytes}`)

  let pos = 0
  let tlvCount = 0

  let type: FrameType | undefined
  let msgId = 0
  let seq = 0
  let pushId = 0
  let flags = 0
  let ts = 0
  let ackPushId = 0
  let payload = bytes.subarray(0, 0)

  while (pos < bytes.length) {
    if (++tlvCount > l.maxTlvCount) throw new Error(`Too many TLVs: > ${l.maxTlvCount}`)

    const tagR = decodeUVarint(bytes, pos)
    pos += tagR.bytesRead

    const lenR = decodeUVarint(bytes, pos)
    pos += lenR.bytesRead

    const len = lenR.value
    if (len > l.maxTlvValueBytes) throw new Error(`TLV value too large: ${len} > ${l.maxTlvValueBytes}`)
    if (pos + len > bytes.length) throw new Error('TLV: truncated value')

    const valueBytes = bytes.subarray(pos, pos + len)
    pos += len

    switch (tagR.value) {
      case ProtocolTags.TYPE: {
        const r = decodeUVarint(valueBytes, 0)
        if (r.bytesRead !== valueBytes.length) throw new Error('type: invalid uvarint length')
        if (!isFrameType(r.value)) throw new Error(`Unknown FrameType: ${r.value}`)
        type = r.value
        break
      }
      case ProtocolTags.MSG_ID: {
        const r = decodeUVarint(valueBytes, 0)
        if (r.bytesRead !== valueBytes.length) throw new Error('msgId: invalid uvarint length')
        msgId = r.value
        break
      }
      case ProtocolTags.SEQ: {
        const r = decodeUVarint(valueBytes, 0)
        if (r.bytesRead !== valueBytes.length) throw new Error('seq: invalid uvarint length')
        seq = r.value
        break
      }
      case ProtocolTags.PUSH_ID: {
        const r = decodeUVarint(valueBytes, 0)
        if (r.bytesRead !== valueBytes.length) throw new Error('pushId: invalid uvarint length')
        pushId = r.value
        break
      }
      case ProtocolTags.FLAGS: {
        const r = decodeUVarint(valueBytes, 0)
        if (r.bytesRead !== valueBytes.length) throw new Error('flags: invalid uvarint length')
        flags = r.value
        break
      }
      case ProtocolTags.TS: {
        const r = decodeUVarint(valueBytes, 0)
        if (r.bytesRead !== valueBytes.length) throw new Error('ts: invalid uvarint length')
        ts = r.value
        break
      }
      case ProtocolTags.ACK_PUSH_ID: {
        const r = decodeUVarint(valueBytes, 0)
        if (r.bytesRead !== valueBytes.length) throw new Error('ackPushId: invalid uvarint length')
        ackPushId = r.value
        break
      }
      case ProtocolTags.PAYLOAD:
        payload = valueBytes
        break
      default:
        break
    }
  }

  if (type === undefined) throw new Error('Missing required tag: type')
  return { type, msgId, seq, pushId, flags, ts, ackPushId, payload }
}
