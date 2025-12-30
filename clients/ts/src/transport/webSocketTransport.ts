import { Transport, TransportEvents, TransportCloseEvent } from './transport.js'

export type WebSocketFactory = (url: string) => WebSocket

function toUint8Array(data: unknown): Promise<Uint8Array> {
  if (data instanceof ArrayBuffer) return Promise.resolve(new Uint8Array(data))
  if (ArrayBuffer.isView(data)) return Promise.resolve(new Uint8Array(data.buffer, data.byteOffset, data.byteLength))
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    return data.arrayBuffer().then((b) => new Uint8Array(b))
  }
  throw new Error(`Unsupported WebSocket message type: ${Object.prototype.toString.call(data)}`)
}

export class WebSocketTransport implements Transport {
  readonly #factory: WebSocketFactory
  #events: TransportEvents = {}
  #ws: WebSocket | undefined

  constructor(factory?: WebSocketFactory) {
    const f = factory ?? ((url) => new WebSocket(url))
    this.#factory = f
  }

  setEvents(events: TransportEvents): void {
    this.#events = events
  }

  get isOpen(): boolean {
    return this.#ws?.readyState === WebSocket.OPEN
  }

  connect(url: string): void {
    const ws = this.#factory(url)
    this.#ws = ws

    if ('binaryType' in ws) {
      ws.binaryType = 'arraybuffer'
    }

    ws.onopen = () => this.#events.onOpen?.()
    ws.onclose = (ev) => {
      const closeEv: TransportCloseEvent = { code: ev.code, reason: ev.reason, wasClean: ev.wasClean }
      this.#events.onClose?.(closeEv)
    }
    ws.onerror = (ev) => this.#events.onError?.(ev)
    ws.onmessage = async (ev) => {
      try {
        const bytes = await toUint8Array(ev.data)
        this.#events.onMessage?.(bytes)
      } catch (e) {
        this.#events.onError?.(e)
      }
    }
  }

  send(data: Uint8Array): void {
    if (!this.#ws) throw new Error('WebSocketTransport is not connected')
    this.#ws.send(data)
  }

  close(code?: number, reason?: string): void {
    this.#ws?.close(code, reason)
  }
}

