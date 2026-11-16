export type Status =
  | 'starting' | 'noBluetooth' | 'scanning' | 'notFound' | 'gaveUp' | 'pick'
  | 'connecting' | 'connected' | 'reconnecting' | 'linkLost'

export type LedState = 'connected' | 'searching' | 'disconnected'

export interface StatusPresentation {
  readonly led: LedState
  readonly label: string
  readonly controlsEnabled: boolean
}

/** How the header LED and the controls behave for each connection status. Only a connected amp enables controls. */
export function statusPresentation(s: Status): StatusPresentation {
  switch (s) {
    case 'connected': return { led: 'connected', label: 'BAĞLI', controlsEnabled: true }
    case 'connecting': return { led: 'searching', label: 'BAĞLANILIYOR…', controlsEnabled: false }
    case 'reconnecting': return { led: 'searching', label: 'YENİDEN BAĞLANILIYOR…', controlsEnabled: false }
    case 'starting': case 'scanning': case 'notFound':
      return { led: 'searching', label: 'ARANIYOR…', controlsEnabled: false }
    case 'gaveUp': case 'linkLost': case 'noBluetooth': case 'pick':
      return { led: 'disconnected', label: 'BAĞLI DEĞİL', controlsEnabled: false }
  }
}
