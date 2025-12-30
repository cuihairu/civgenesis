export type TransportCloseEvent = Readonly<{
  code?: number
  reason?: string
  wasClean?: boolean
}>

export type TransportEvents = Readonly<{
  onOpen?: () => void
  onClose?: (ev: TransportCloseEvent) => void
  onError?: (err: unknown) => void
  onMessage?: (data: Uint8Array) => void
}>

export interface Transport {
  setEvents(events: TransportEvents): void
  connect(url: string): void
  send(data: Uint8Array): void
  close(code?: number, reason?: string): void
  readonly isOpen: boolean
}

