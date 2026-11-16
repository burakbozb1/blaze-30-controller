import { expect, test, type Page } from '@playwright/test'
import { launch, resize, type Launched } from './harness'

/* eslint-disable @typescript-eslint/no-explicit-any */
const written = (page: Page) =>
  page.evaluate(() => (window as any).__blazeTest.transport.written.map((f: Uint8Array) => Array.from(f)) as number[][])
const lastFrame = async (page: Page) => (await written(page)).at(-1)!

let l: Launched
test.beforeEach(async () => {
  l = await launch({ width: 1100, height: 650 })
  await l.page.waitForSelector('[data-testid="pedal-CLEAN"]:not([disabled])') // connected (fake amp)
})
test.afterEach(async () => { await l.app.close() })

test('a pedal sends its tone frame and shows the lit ring', async () => {
  const { page } = l
  await page.click('[data-testid="pedal-METAL"]')
  await expect.poll(() => lastFrame(page)).toEqual([0xfe, 0x0d, 0x02, 0x03, 0x00, 4, 0, 0, 0, 0xff])
  await expect(page.locator('[data-testid="pedal-METAL"]')).toHaveAttribute('aria-checked', 'true')
  await expect(page.locator('[data-testid="pedal-CLEAN"]')).toHaveAttribute('aria-checked', 'false')
})

test('dragging a knob up raises the value and writes it', async () => {
  const { page } = l
  const b = (await page.locator('[data-testid="knob-GAIN"]').boundingBox())!
  const x = b.x + b.width / 2
  const y = b.y + b.height / 2
  await page.mouse.move(x, y)
  await page.mouse.down()
  await page.mouse.move(x, y - 22, { steps: 6 }) // 22 px of 220 over 0..30 = +3
  await page.mouse.up()
  await expect.poll(() => lastFrame(page)).toEqual([0xfe, 0x0d, 0x02, 0x03, 0x02, 18, 0, 0, 0, 0xff])
  await expect(page.locator('[data-testid="knob-GAIN"]')).toHaveAttribute('aria-valuenow', '18')
})

test('double click opens the popup; plus twice steps twice even without an echo; TAMAM closes', async () => {
  const { page } = l
  const knob = page.locator('[data-testid="knob-GAIN"]')
  await knob.dblclick()
  await expect(page.locator('[data-testid="popup-title"]')).toHaveText('GAIN')
  await expect(page.locator('[data-testid="popup-value"]')).toHaveText('15')
  await page.click('[data-testid="popup-plus"]')
  await page.click('[data-testid="popup-plus"]')
  const w = await written(page)
  expect(w.slice(-2).map((f) => f[5])).toEqual([16, 17])
  await page.click('[data-testid="popup-ok"]')
  await expect(page.locator('[data-testid="popup"]')).toHaveCount(0)
})

test('the popup closes by itself when the link drops', async () => {
  const { page } = l
  const knob = page.locator('[data-testid="knob-GAIN"]')
  await knob.dblclick()
  await expect(page.locator('[data-testid="popup"]')).toHaveCount(1)
  await page.evaluate(() => (window as any).__blazeTest.transport.dropConnection())
  await expect(page.locator('[data-testid="popup"]')).toHaveCount(0)
  await expect(page.locator('[data-testid="knob-GAIN"]')).toHaveAttribute('aria-disabled', 'true')
  await expect(page.locator('[data-testid="connection-card"]')).toBeVisible()
})

test('delay power writes 0 then restores the previous level', async () => {
  const { page } = l
  await page.evaluate(() => (window as any).__blazeTest.app.setMasterDelay(30))
  await expect(page.locator('[data-testid="power-Delay"]')).toHaveAttribute('aria-checked', 'true')
  await page.click('[data-testid="power-Delay"]')
  await expect.poll(() => lastFrame(page)).toEqual([0xfe, 0x0d, 0x02, 0x02, 0x00, 0, 0, 0, 0, 0xff])
  await page.waitForTimeout(1100)
  await page.click('[data-testid="power-Delay"]')
  await expect.poll(() => lastFrame(page)).toEqual([0xfe, 0x0d, 0x02, 0x02, 0x00, 30, 0, 0, 0, 0xff])
})

test('SIFIRLA writes seven flat bands in order', async () => {
  const { page } = l
  await page.evaluate(() => { const a = (window as any).__blazeTest.app; a.setEq(2, 3); a.setEq(6, 18) })
  await page.click('[data-testid="tab-EQ"]')
  await expect(page.locator('[data-testid="fader-value-400"]')).toHaveText('3')
  await page.click('[data-testid="btn-SIFIRLA"]')
  await expect.poll(async () => (await written(page)).slice(-7).map((f) => [f[4], f[5]])).toEqual([1, 2, 3, 4, 5, 6, 7].map((n) => [n, 10]))
  await expect(page.locator('[data-testid="fader-value-400"]')).toHaveText('10')
})

test('tapping a fader sets the value where it was tapped and shows what it sent', async () => {
  const { page } = l
  await page.click('[data-testid="tab-EQ"]')
  const box = (await page.locator('[data-testid="fader-800"]').boundingBox())!
  await page.mouse.click(box.x + box.width / 2, box.y + 7.5 + (box.height - 15) * 0.25) // 75% of the way up
  await expect.poll(async () => (await lastFrame(page)).slice(3, 6)).toEqual([0x04, 4, 15])
  await expect(page.locator('[data-testid="fader-value-800"]')).toHaveText('15')
})

test('tabs sit in the header row when wide and below it when narrow', async () => {
  const { app, page } = l
  const logoBottom = async () => { const b = (await page.locator('[data-testid="logo"]').boundingBox())!; return b.y + b.height }
  const tabTop = async () => (await page.locator('[data-testid="tab-AMP"]').boundingBox())!.y
  expect(await tabTop()).toBeLessThan(await logoBottom())
  await resize(app, 600, 820)
  await expect.poll(async () => (await tabTop()) >= (await logoBottom()) - 1).toBe(true)
})

test('knobs and faders opt out of browser touch scrolling and zooming', async () => {
  const { page } = l
  const ta = (id: string) => page.locator(`[data-testid="${id}"]`).evaluate((el) => getComputedStyle(el).touchAction)
  expect(await ta('knob-GAIN')).toBe('none')
  await page.click('[data-testid="tab-EQ"]')
  expect(await ta('fader-100')).toBe('none')
})

test('the tuner listens only on the AKORT tab and shows the played note', async () => {
  const { page } = l
  const mic = () => page.evaluate(() => { const m = (window as any).__blazeTest.mic; return [m.starts, m.releases] })
  expect(await mic()).toEqual([0, 0])
  await page.click('[data-testid="tab-AKORT"]')
  await expect.poll(mic).toEqual([1, 0])
  await page.evaluate(() => {
    const m = (window as any).__blazeTest.mic
    const frame = () => Int16Array.from({ length: 4096 }, (_, i) => Math.trunc(Math.sin((2 * Math.PI * 82.41 * i) / 44100) * 0.3 * 32767))
    for (let i = 0; i < 6; i++) m.push(frame())
  })
  await expect(page.locator('[data-testid="tuner-note"]')).toHaveText('E2')
  await expect(page.locator('[data-testid="tuner-string"]')).toHaveText('6. tel')
  await page.click('[data-testid="tab-AMP"]')
  await expect.poll(mic).toEqual([1, 1])
})

test('the logo opens the about panel and the button asks the main process to open LinkedIn', async () => {
  const { app, page } = l
  await page.click('[data-testid="logo"]')
  await expect(page.locator('[data-testid="drawer"]')).toHaveClass(/open/)
  await expect(page.getByText('Geliştirici: Burak Boz')).toBeVisible()
  await expect(page.getByText('Yalnızca Kozmos Blaze 30 amfisiyle çalışır')).toBeVisible()
  await page.getByRole('button', { name: /LINKEDIN.*BURAK BOZ/ }).click()
  await expect.poll(() => app.evaluate(() => (globalThis as { __linkedinOpened?: number }).__linkedinOpened ?? 0)).toBe(1)
})

test('the app makes no network requests', async () => {
  const { page } = l
  const urls: string[] = []
  page.on('request', (r) => urls.push(r.url()))
  await page.reload()
  await page.waitForSelector('[data-testid="pedal-CLEAN"]')
  await page.click('[data-testid="tab-EQ"]')
  await page.click('[data-testid="tab-AKORT"]')
  expect(urls.length).toBeGreaterThan(0)
  expect(urls.filter((u) => !u.startsWith('file:') && !u.startsWith('data:'))).toEqual([])
})

test('wide AMP tab: gain/effect, delay and reverb are three equal full-width rows with big controls', async () => {
  const { page } = l
  const panels = page.locator('[data-testid="content"] .amptab .panel')
  await expect(panels).toHaveCount(3)
  const boxes = await panels.evaluateAll((els) => els.map((e) => { const r = e.getBoundingClientRect(); return { x: r.x, w: r.width, y: r.y, h: r.height } }))
  const content = (await page.locator('[data-testid="content"]').boundingBox())!
  for (const b of boxes) expect(b.w).toBeGreaterThan(content.width - 40) // full width
  expect(Math.abs(boxes[0].h - boxes[1].h)).toBeLessThan(2) // equal heights
  expect(Math.abs(boxes[1].h - boxes[2].h)).toBeLessThan(2)
  expect(boxes[1].y).toBeGreaterThan(boxes[0].y + boxes[0].h - 1) // stacked
  expect(boxes[2].y).toBeGreaterThan(boxes[1].y + boxes[1].h - 1)
  const knob = (await page.locator('[data-testid="knob-Delay ANA"]').boundingBox())!
  expect(knob.width).toBeGreaterThanOrEqual(64)
  // nothing is clipped or overflowing inside its row
  for (let i = 0; i < 3; i++) {
    const ok = await panels.nth(i).evaluate((el) => el.scrollHeight <= el.clientHeight + 1 && el.scrollWidth <= el.clientWidth + 1)
    expect(ok).toBe(true)
  }
})

test('the music (aux) knob next to ANA SES writes the aux frame', async () => {
  const { app, page } = l
  await resize(app, 1100, 650)
  await page.waitForTimeout(150)
  const b = (await page.locator('[data-testid="knob-Müzik sesi"]').boundingBox())!
  const x = b.x + b.width / 2
  const y = b.y + b.height / 2
  await page.mouse.move(x, y)
  await page.mouse.down()
  await page.mouse.move(x, y + 44, { steps: 6 }) // dragging down keeps the pointer inside the window: 44 px of 220 over 0..50 = -10
  await page.mouse.up()
  await expect.poll(() => lastFrame(page)).toEqual([0xfe, 0x0d, 0x04, 0x01, 0x00, 0, 0, 0, 0, 0xff])
  await expect(page.locator('[data-testid="knob-Müzik sesi"]')).toHaveAttribute('aria-valuenow', '0')
})

test('the two header knobs never overlap each other, the logo or the tabs', async () => {
  const { app, page } = l
  const box = async (id: string) => (await page.locator(`[data-testid="${id}"]`).boundingBox())!
  const overlap = (a: { x: number; y: number; width: number; height: number }, c: typeof a) =>
    a.x < c.x + c.width && c.x < a.x + a.width && a.y < c.y + c.height && c.y < a.y + a.height
  for (const [w, h] of [[1100, 650], [800, 480], [600, 820], [420, 800]]) {
    await resize(app, w, h)
    await page.waitForTimeout(150)
    const main = await box('knob-ANA SES')
    const music = await box('knob-Müzik sesi')
    const logo = await box('logo')
    const tabs = await box('tab-AKORT')
    expect(overlap(main, music), `${w}x${h}: volume knobs overlap`).toBe(false)
    expect(overlap(music, logo), `${w}x${h}: music knob over the logo`).toBe(false)
    expect(overlap(music, tabs), `${w}x${h}: music knob over the tabs`).toBe(false)
    expect(music.x + music.width, `${w}x${h}: music knob off screen`).toBeLessThanOrEqual(w + 1)
  }
})

