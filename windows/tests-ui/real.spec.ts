import { expect, test } from '@playwright/test'
import { launch } from './harness'

test('the real (non-fake) app starts, shows a status and never crashes without a Bluetooth radio', async () => {
  const { app, page } = await launch({ fake: false, width: 1100, height: 650 })
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.waitForSelector('[data-testid="led"]')
  await page.waitForTimeout(1500)
  // controls stay disabled and a card explains why (or the scan is still running)
  await expect(page.locator('[data-testid="pedal-CLEAN"]')).toBeDisabled()
  await expect(page.locator('[data-testid="connection-card"]')).toBeVisible()
  expect(errors).toEqual([])
  await app.close()
})
