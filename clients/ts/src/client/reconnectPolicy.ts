export type ReconnectPolicy = Readonly<{
  nextDelayMs: (attempt: number) => number
}>

export function exponentialBackoffPolicy(opts: {
  baseMs?: number
  maxMs?: number
  jitterRatio?: number
} = {}): ReconnectPolicy {
  const baseMs = opts.baseMs ?? 200
  const maxMs = opts.maxMs ?? 30_000
  const jitterRatio = opts.jitterRatio ?? 0.2

  return {
    nextDelayMs(attempt: number) {
      const exp = Math.min(maxMs, baseMs * Math.pow(2, Math.max(0, attempt - 1)))
      const jitter = exp * jitterRatio * (Math.random() * 2 - 1)
      return Math.max(0, Math.floor(exp + jitter))
    }
  }
}

