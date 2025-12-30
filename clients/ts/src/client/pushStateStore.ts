export interface PushStateStore {
  getLastAppliedPushId(): number
  setLastAppliedPushId(pushId: number): void
}

export class MemoryPushStateStore implements PushStateStore {
  #lastAppliedPushId = 0

  getLastAppliedPushId(): number {
    return this.#lastAppliedPushId
  }

  setLastAppliedPushId(pushId: number): void {
    if (!Number.isSafeInteger(pushId) || pushId < 0) throw new Error(`Invalid pushId: ${pushId}`)
    this.#lastAppliedPushId = pushId
  }
}

export class LocalStoragePushStateStore implements PushStateStore {
  readonly #key: string

  constructor(key: string) {
    this.#key = key
  }

  getLastAppliedPushId(): number {
    try {
      const raw = globalThis.localStorage?.getItem(this.#key)
      if (!raw) return 0
      const v = Number(raw)
      if (!Number.isSafeInteger(v) || v < 0) return 0
      return v
    } catch {
      return 0
    }
  }

  setLastAppliedPushId(pushId: number): void {
    if (!Number.isSafeInteger(pushId) || pushId < 0) throw new Error(`Invalid pushId: ${pushId}`)
    globalThis.localStorage?.setItem(this.#key, String(pushId))
  }
}

