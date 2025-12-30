import { Frame } from '../protocol/frame.js'

export class CivGenesisError extends Error {
  override name: string = 'CivGenesisError'
  readonly code?: string
  readonly frame?: Frame
  readonly cause?: unknown

  constructor(message: string, opts: { code?: string; frame?: Frame; cause?: unknown } = {}) {
    super(message)
    this.code = opts.code
    this.frame = opts.frame
    this.cause = opts.cause
  }
}

export class TimeoutError extends CivGenesisError {
  override name: string = 'TimeoutError'

  constructor(message = 'request timeout', opts: { frame?: Frame; cause?: unknown } = {}) {
    super(message, { code: 'TIMEOUT', frame: opts.frame, cause: opts.cause })
  }
}

export class DisconnectedError extends CivGenesisError {
  override name: string = 'DisconnectedError'

  constructor(message = 'disconnected', opts: { cause?: unknown } = {}) {
    super(message, { code: 'DISCONNECTED', cause: opts.cause })
  }
}

export class ProtocolError extends CivGenesisError {
  override name: string = 'ProtocolError'

  constructor(message: string, opts: { frame?: Frame; cause?: unknown } = {}) {
    super(message, { code: 'PROTOCOL', frame: opts.frame, cause: opts.cause })
  }
}
