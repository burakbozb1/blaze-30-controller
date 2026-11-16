import { describe, expect, test } from 'vitest'
import { CHOOSER_PATIENCE_MS, CSP, LINKEDIN_URL, isAllowedExternal, isAllowedPermission, pickBlaze } from './guards'

describe('shell guards', () => {
  test('only the exact LinkedIn profile URL may be opened externally', () => {
    expect(isAllowedExternal(LINKEDIN_URL)).toBe(true)
    for (const bad of [
      'https://www.linkedin.com/in/someoneelse', 'http://www.linkedin.com/in/burakbozb1', 'https://evil.example/',
      'file:///C:/Windows/System32/cmd.exe', 'javascript:alert(1)', '', `${LINKEDIN_URL}/../x`,
    ]) expect(isAllowedExternal(bad)).toBe(false)
  })

  test('pickBlaze takes the first BLAZE-30 device and ignores others', () => {
    const list = [
      { deviceId: 'a', deviceName: 'JBL Flip' }, { deviceId: 'b', deviceName: 'BLAZE-30 APP' }, { deviceId: 'c', deviceName: 'BLAZE-30 APP' },
    ]
    expect(pickBlaze(list)?.deviceId).toBe('b')
    expect(pickBlaze([{ deviceId: 'a', deviceName: 'JBL Flip' }, { deviceId: 'z', deviceName: '' }])).toBeNull()
    expect(pickBlaze([])).toBeNull()
  })

  test('only audio capture is a permitted permission', () => {
    expect(isAllowedPermission('media', { mediaTypes: ['audio'] })).toBe(true)
    expect(isAllowedPermission('media', { mediaTypes: ['audio', 'video'] })).toBe(false)
    expect(isAllowedPermission('media', { mediaTypes: ['video'] })).toBe(false)
    expect(isAllowedPermission('media', undefined)).toBe(false)
    for (const p of ['geolocation', 'notifications', 'clipboard-read', 'bluetooth', 'openExternal']) {
      expect(isAllowedPermission(p, { mediaTypes: ['audio'] })).toBe(false)
    }
  })

  test('the CSP allows no remote origins', () => {
    expect(CSP).toContain("default-src 'self'")
    expect(CSP).not.toMatch(/https?:/)
    expect(CSP).not.toContain("'unsafe-eval'")
  })

  test('the chooser waits long enough for an amp that is switched on after the app starts', () => {
    // Later scan windows have no user gesture and reuse this one request, so it has to outlive them (8 x ~7 s).
    expect(CHOOSER_PATIENCE_MS).toBeGreaterThanOrEqual(30_000)
  })
})
