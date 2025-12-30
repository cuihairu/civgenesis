export type VarintDecodeResult<T> = Readonly<{ value: T; bytesRead: number }>

const MAX_SAFE_BIGINT = BigInt(Number.MAX_SAFE_INTEGER)

export function encodeUVarint(value: number | bigint): Uint8Array {
  let v = typeof value === 'bigint' ? value : BigInt(value)
  if (v < 0n) throw new Error('uvarint must be >= 0')
  const out = new Uint8Array(10)
  let i = 0
  while (v >= 0x80n) {
    out[i++] = Number((v & 0x7fn) | 0x80n)
    v >>= 7n
  }
  out[i++] = Number(v)
  return out.subarray(0, i)
}

export function decodeUVarintBigInt(bytes: Uint8Array, offset = 0): VarintDecodeResult<bigint> {
  let x = 0n
  let s = 0n
  for (let i = 0; i < 10; i++) {
    const b = bytes[offset + i]
    if (b === undefined) throw new Error('uvarint: truncated')
    if (b < 0x80) {
      x |= BigInt(b) << s
      return { value: x, bytesRead: i + 1 }
    }
    x |= BigInt(b & 0x7f) << s
    s += 7n
  }
  throw new Error('uvarint: overflow')
}

export function decodeUVarint(bytes: Uint8Array, offset = 0): VarintDecodeResult<number> {
  const r = decodeUVarintBigInt(bytes, offset)
  if (r.value > MAX_SAFE_BIGINT) {
    throw new Error(`uvarint: value exceeds MAX_SAFE_INTEGER: ${r.value.toString()}`)
  }
  return { value: Number(r.value), bytesRead: r.bytesRead }
}

