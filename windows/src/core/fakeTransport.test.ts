import { expect, test } from 'vitest'
import { FakeTransport } from './fakeTransport'

const device = { name: 'BLAZE-30 APP', address: 'AA:BB:CC:00:00:01' }

test('writes after connect are recorded in order', async () => {
  const t = new FakeTransport()
  await t.connect(device)
  await t.write(Uint8Array.of(1)); await t.write(Uint8Array.of(2)); await t.write(Uint8Array.of(3))
  expect(t.written).toHaveLength(3)
  expect(t.written[0]).toEqual(Uint8Array.of(1))
  expect(t.written[2]).toEqual(Uint8Array.of(3))
  expect(t.connection.value.kind).toBe('connected')
})

test('write while disconnected rejects', async () => {
  const t = new FakeTransport()
  await expect(t.write(Uint8Array.of(1))).rejects.toThrow()
  expect(t.written).toHaveLength(0)
})

test('a dropped connection moves to disconnected and blocks writes', async () => {
  const t = new FakeTransport()
  await t.connect(device)
  t.dropConnection()
  expect(t.connection.value.kind).toBe('disconnected')
  await expect(t.write(Uint8Array.of(9))).rejects.toThrow()
})

test('disconnect moves to disconnected', async () => {
  const t = new FakeTransport()
  await t.connect(device)
  t.disconnect()
  expect(t.connection.value.kind).toBe('disconnected')
})

test('incoming frames reach listeners in order, as copies', () => {
  const t = new FakeTransport()
  const seen: Uint8Array[] = []
  const off = t.onIncoming((f) => seen.push(f))
  const src = Uint8Array.of(1)
  t.emitFromAmp(src); t.emitFromAmp(Uint8Array.of(2))
  src[0] = 99
  expect(seen).toEqual([Uint8Array.of(1), Uint8Array.of(2)])
  off(); t.emitFromAmp(Uint8Array.of(3))
  expect(seen).toHaveLength(2)
})
