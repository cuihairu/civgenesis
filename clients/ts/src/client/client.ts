import { decodeFrame, encodeFrame, TlvFrameCodecLimits } from '../codec/tlvFrameCodec.js'
import { Frame } from '../protocol/frame.js'
import { FrameType } from '../protocol/frameType.js'
import { ProtocolFlags } from '../protocol/protocolFlags.js'
import { Transport } from '../transport/transport.js'
import { DisconnectedError, ProtocolError, TimeoutError } from './errors.js'
import { MemoryPushStateStore, PushStateStore } from './pushStateStore.js'
import { exponentialBackoffPolicy, ReconnectPolicy } from './reconnectPolicy.js'

export type RequestOptions = Readonly<{
  timeoutMs?: number
  retries?: number
  retryDelayMs?: number
}>

export type CivGenesisClientOptions = Readonly<{
  codecLimits?: Partial<TlvFrameCodecLimits>
  transport: Transport
  requestTimeoutMs?: number
  maxInFlightRequests?: number
  ackFlushDelayMs?: number
  ackPiggyback?: boolean
  pushStateStore?: PushStateStore
  autoReconnect?: boolean
  reconnectPolicy?: ReconnectPolicy
  onPush?: (frame: Frame) => void | Promise<void>
  onProtocolError?: (err: Error) => void
  onDisconnected?: (err?: unknown) => void
  onReconnected?: (attempt: number) => void
}>

type PendingReq = {
  readonly msgId: number
  readonly seq: number
  readonly frame: Frame
  readonly createdAtMs: number
  timeoutId: ReturnType<typeof setTimeout> | undefined
  resolve: (f: Frame) => void
  reject: (e: Error) => void
}

export class CivGenesisClient {
  readonly #opts: CivGenesisClientOptions
  readonly #codecLimits: Partial<TlvFrameCodecLimits>
  readonly #transport: Transport

  #url: string | undefined
  #nextSeq = 1
  #pending = new Map<number, PendingReq>()
  #connectPromise: Promise<void> | undefined
  #connectResolve: (() => void) | undefined
  #connectReject: ((e: Error) => void) | undefined
  #connectTimeoutId: ReturnType<typeof setTimeout> | undefined
  #connected = false
  #manualClose = false

  readonly #pushState: PushStateStore
  #processingPush: Promise<void> = Promise.resolve()
  #ackFlushTimer: ReturnType<typeof setTimeout> | undefined
  #lastAckedPushId = 0

  #reconnectAttempt = 0
  readonly #reconnectPolicy: ReconnectPolicy
  #reconnectTimer: ReturnType<typeof setTimeout> | undefined

  constructor(opts: CivGenesisClientOptions) {
    this.#opts = opts
    this.#codecLimits = opts.codecLimits ?? {}
    this.#transport = opts.transport
    this.#pushState = opts.pushStateStore ?? new MemoryPushStateStore()
    this.#reconnectPolicy = opts.reconnectPolicy ?? exponentialBackoffPolicy()

    this.#transport.setEvents({
      onOpen: () => this.#onOpen(),
      onMessage: (data) => void this.#onMessage(data),
      onClose: (ev) => this.#onClose(ev),
      onError: (err) => this.#opts.onProtocolError?.(err instanceof Error ? err : new Error(String(err)))
    })
  }

  get isConnected(): boolean {
    return this.#connected && this.#transport.isOpen
  }

  get lastAppliedPushId(): number {
    return this.#pushState.getLastAppliedPushId()
  }

  connect(url: string): Promise<void> {
    if (this.isConnected) return Promise.resolve()
    this.#url = url
    this.#manualClose = false
    if (this.#connectPromise) return this.#connectPromise

    this.#connectPromise = new Promise<void>((resolve, reject) => {
      this.#connectResolve = resolve
      this.#connectReject = reject
      this.#connectTimeoutId = setTimeout(() => {
        this.#connectTimeoutId = undefined
        this.#connectPromise = undefined
        this.#connectResolve = undefined
        this.#connectReject = undefined
        reject(new TimeoutError('connect timeout'))
      }, 10_000)

      try {
        this.#transport.connect(url)
      } catch (e) {
        if (this.#connectTimeoutId) clearTimeout(this.#connectTimeoutId)
        this.#connectTimeoutId = undefined
        this.#connectPromise = undefined
        this.#connectResolve = undefined
        this.#connectReject = undefined
        reject(e instanceof Error ? e : new Error(String(e)))
      }
    })

    return this.#connectPromise
  }

  disconnect(code?: number, reason?: string) {
    this.#manualClose = true
    if (this.#reconnectTimer) clearTimeout(this.#reconnectTimer)
    this.#reconnectTimer = undefined
    this.#transport.close(code, reason)
  }

  send(frame: Frame): void {
    this.#assertConnected()
    const outbound = this.#maybePiggybackAck(frame)
    this.#transport.send(encodeFrame(outbound, this.#codecLimits))
  }

  async request(msgId: number, payload: Uint8Array, options: RequestOptions = {}): Promise<Frame> {
    this.#assertConnected()

    if (!Number.isSafeInteger(msgId) || msgId <= 0) throw new Error(`Invalid msgId: ${msgId}`)
    if (this.#pending.size >= (this.#opts.maxInFlightRequests ?? 1024)) {
      throw new DisconnectedError('too many in-flight requests')
    }

    const timeoutMs = options.timeoutMs ?? this.#opts.requestTimeoutMs ?? 3000
    const retries = options.retries ?? 0
    const retryDelayMs = options.retryDelayMs ?? 0

    const seq = this.#nextSeq++
    const frame: Frame = { type: FrameType.REQ, msgId, seq, payload }

    for (let attempt = 0; attempt <= retries; attempt++) {
      try {
        return await this.#requestOnce(frame, timeoutMs)
      } catch (e) {
        if (!(e instanceof TimeoutError) || attempt === retries) throw e
        if (retryDelayMs > 0) await new Promise((r) => setTimeout(r, retryDelayMs))
      }
    }
    throw new TimeoutError('request timeout')
  }

  #requestOnce(frame: Frame, timeoutMs: number): Promise<Frame> {
    const seq = frame.seq ?? 0
    if (seq <= 0) throw new Error('REQ seq must be >0')

    return new Promise<Frame>((resolve, reject) => {
      const createdAtMs = Date.now()
      const pending: PendingReq = {
        msgId: frame.msgId ?? 0,
        seq,
        frame,
        createdAtMs,
        timeoutId: undefined,
        resolve: (f) => resolve(f),
        reject: (err) => reject(err)
      }

      pending.timeoutId = setTimeout(() => {
        this.#pending.delete(seq)
        reject(new TimeoutError(`request timeout (seq=${seq})`, { frame }))
      }, timeoutMs)

      this.#pending.set(seq, pending)
      this.#transport.send(encodeFrame(this.#maybePiggybackAck(frame), this.#codecLimits))
    })
  }

  #assertConnected() {
    if (!this.isConnected) throw new DisconnectedError()
  }

  #onOpen() {
    this.#connected = true
    this.#reconnectAttempt = 0
    if (this.#reconnectTimer) clearTimeout(this.#reconnectTimer)
    this.#reconnectTimer = undefined
    if (this.#connectTimeoutId) clearTimeout(this.#connectTimeoutId)
    this.#connectTimeoutId = undefined
    this.#connectPromise = undefined
    const resolve = this.#connectResolve
    this.#connectResolve = undefined
    this.#connectReject = undefined
    resolve?.()
  }

  async #onMessage(data: Uint8Array) {
    let frame: Frame
    try {
      frame = decodeFrame(data, this.#codecLimits)
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e))
      this.#opts.onProtocolError?.(err)
      this.#transport.close(1002, 'protocol error')
      return
    }

    switch (frame.type) {
      case FrameType.RESP:
        this.#handleResp(frame)
        return
      case FrameType.PUSH:
        await this.#enqueuePush(frame)
        return
      case FrameType.PING:
        this.send({ type: FrameType.PONG })
        return
      case FrameType.PONG:
      case FrameType.ACK:
      case FrameType.REQ:
      default:
        return
    }
  }

  #handleResp(frame: Frame) {
    const seq = frame.seq ?? 0
    if (seq <= 0) {
      this.#opts.onProtocolError?.(new ProtocolError('RESP missing seq', { frame }))
      return
    }
    const pending = this.#pending.get(seq)
    if (!pending) return
    this.#pending.delete(seq)
    if (pending.timeoutId) clearTimeout(pending.timeoutId)

    if ((frame.flags ?? 0) & ProtocolFlags.ERROR) {
      pending.reject(new ProtocolError('server returned ERROR', { frame }))
      return
    }
    pending.resolve(frame)
  }

  async #enqueuePush(frame: Frame) {
    this.#processingPush = this.#processingPush.then(() => this.#handlePush(frame)).catch((e) => {
      const err = e instanceof Error ? e : new Error(String(e))
      this.#opts.onProtocolError?.(err)
      this.#transport.close(1008, 'push sequence gap')
    })
    await this.#processingPush
  }

  async #handlePush(frame: Frame) {
    const pushId = frame.pushId ?? 0
    if (pushId <= 0) throw new ProtocolError('PUSH missing pushId', { frame })

    const lastApplied = this.#pushState.getLastAppliedPushId()
    if (pushId <= lastApplied) {
      if ((frame.flags ?? 0) & ProtocolFlags.ACK_REQUIRED) {
        this.#sendAck(lastApplied, true)
      }
      return
    }

    if (pushId !== lastApplied + 1) {
      throw new ProtocolError(`PUSH gap detected: lastApplied=${lastApplied}, got=${pushId}`, { frame })
    }

    await this.#opts.onPush?.(frame)
    this.#pushState.setLastAppliedPushId(pushId)

    if ((frame.flags ?? 0) & ProtocolFlags.ACK_REQUIRED) {
      this.#scheduleAckFlush()
    }
  }

  #scheduleAckFlush() {
    if (this.#opts.ackPiggyback) {
      const lastApplied = this.#pushState.getLastAppliedPushId()
      this.#maybeScheduleAckOnly(lastApplied)
      return
    }

    this.#maybeScheduleAckOnly(this.#pushState.getLastAppliedPushId())
  }

  #maybeScheduleAckOnly(lastAppliedPushId: number) {
    if (this.#ackFlushTimer) return
    const delay = this.#opts.ackFlushDelayMs ?? 200
    this.#ackFlushTimer = setTimeout(() => {
      this.#ackFlushTimer = undefined
      this.#sendAck(this.#pushState.getLastAppliedPushId(), false)
    }, delay)
  }

  #sendAck(pushId: number, force: boolean) {
    if (!this.isConnected) return
    if (pushId <= 0) return
    if (!force && pushId <= this.#lastAckedPushId) return
    if (pushId > this.#lastAckedPushId) this.#lastAckedPushId = pushId
    this.#transport.send(encodeFrame({ type: FrameType.ACK, pushId }, this.#codecLimits))
  }

  #maybePiggybackAck(frame: Frame): Frame {
    if (!this.#opts.ackPiggyback) return frame
    const lastApplied = this.#pushState.getLastAppliedPushId()
    if (lastApplied <= 0 || lastApplied <= this.#lastAckedPushId) return frame
    this.#lastAckedPushId = lastApplied
    return { ...frame, ackPushId: lastApplied }
  }

  #onClose(ev: unknown) {
    this.#connected = false
    this.#connectPromise = undefined
    if (this.#connectTimeoutId) clearTimeout(this.#connectTimeoutId)
    this.#connectTimeoutId = undefined
    this.#connectReject?.(new DisconnectedError('connect failed'))
    this.#connectResolve = undefined
    this.#connectReject = undefined

    if (this.#ackFlushTimer) clearTimeout(this.#ackFlushTimer)
    this.#ackFlushTimer = undefined

    this.#opts.onDisconnected?.(ev)

    for (const [seq, pending] of this.#pending) {
      this.#pending.delete(seq)
      if (pending.timeoutId) clearTimeout(pending.timeoutId)
      pending.reject(new DisconnectedError(`disconnected (seq=${seq})`))
    }

    if (this.#manualClose || !this.#opts.autoReconnect) return

    const url = this.#url
    if (!url) return

    this.#scheduleReconnect(url)
  }

  #scheduleReconnect(url: string) {
    if (this.#reconnectTimer) return
    const attempt = ++this.#reconnectAttempt
    const delayMs = this.#reconnectPolicy.nextDelayMs(attempt)
    this.#reconnectTimer = setTimeout(async () => {
      this.#reconnectTimer = undefined
      if (this.#manualClose) return
      try {
        await this.connect(url)
        this.#opts.onReconnected?.(attempt)
      } catch (e) {
        this.#opts.onProtocolError?.(e instanceof Error ? e : new Error(String(e)))
        if (!this.#manualClose) this.#scheduleReconnect(url)
      }
    }, delayMs)
  }
}
