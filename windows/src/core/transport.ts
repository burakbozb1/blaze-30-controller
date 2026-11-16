import type { Observable } from './observable'

export interface FoundDevice {
  readonly name: string
  readonly address: string
}

export type ConnectionState =
  | { kind: 'disconnected' }
  | { kind: 'connecting' }
  | { kind: 'connected' }
  | { kind: 'failed'; reason: string }

export const DISCONNECTED: ConnectionState = { kind: 'disconnected' }
export const CONNECTING: ConnectionState = { kind: 'connecting' }
export const CONNECTED: ConnectionState = { kind: 'connected' }

/** Byte-level link to the amp. Everything above this layer is Bluetooth-free and unit-testable. */
export interface AmpTransport {
  readonly connection: Observable<ConnectionState>

  /** Notifications from the amp, one callback per notification (bytes copied). Returns an unsubscribe function. */
  onIncoming(cb: (frame: Uint8Array) => void): () => void

  /** Yields Blaze devices as they are found; aborting the signal stops the scan. */
  scan(signal: AbortSignal): AsyncIterable<FoundDevice>

  connect(device: FoundDevice): Promise<void>

  /** Rejects with an Error when not connected. Resolves after the link accepted the bytes. */
  write(bytes: Uint8Array): Promise<void>

  disconnect(): void
}
