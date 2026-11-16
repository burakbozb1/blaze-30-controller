/** Pure decisions of the Electron shell, kept free of Electron so Vitest can check them. */

/**
 * How long the single Bluetooth chooser waits for the amp. Only the first scan has a user gesture; later scan windows reuse
 * that same pending request, so it must outlive them (8 windows of ~7 s), otherwise an amp switched on late is never found.
 */
export const CHOOSER_PATIENCE_MS = 30_000

export const LINKEDIN_URL = 'https://www.linkedin.com/in/burakbozb1'

/** Only the developer's LinkedIn page may ever be opened in the system browser. */
export function isAllowedExternal(url: string): boolean {
  return url === LINKEDIN_URL
}

export interface Candidate {
  deviceId: string
  deviceName: string
}

/** The first discovered device whose name starts with BLAZE-30 (the amp advertises as "BLAZE-30 APP"). */
export function pickBlaze(list: readonly Candidate[]): Candidate | null {
  return list.find((d) => d.deviceName.toUpperCase().startsWith('BLAZE-30')) ?? null
}

/** Only audio capture, requested by the app's own window, is ever granted. */
export function isAllowedPermission(permission: string, details: { mediaTypes?: readonly string[] } | undefined): boolean {
  if (permission !== 'media') return false
  const types = details?.mediaTypes
  return !!types && types.length > 0 && types.every((t) => t === 'audio')
}

export const CSP =
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
  "connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'"
