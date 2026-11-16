import { _electron as electron, type ElectronApplication, type Page } from '@playwright/test'

export interface Launched {
  app: ElectronApplication
  page: Page
}

/** Starts the built Electron app. fake=true wires a fake amp and fake microphone into the page (never the default). */
export async function launch(o: { fake?: boolean; width?: number; height?: number } = {}): Promise<Launched> {
  // ELECTRON_RUN_AS_NODE (set by some IDE hosts) would make electron.exe behave as plain Node: remove it.
  const env = { ...process.env, BLAZE_FAKE: o.fake === false ? '0' : '1' } as Record<string, string>
  delete env.ELECTRON_RUN_AS_NODE
  const app = await electron.launch({ args: ['.'], cwd: process.cwd(), env })
  const page = await app.firstWindow()
  await page.waitForLoadState('domcontentloaded')
  if (o.width && o.height) await resize(app, o.width, o.height)
  return { app, page }
}

export async function resize(app: ElectronApplication, width: number, height: number): Promise<void> {
  await app.evaluate(({ BrowserWindow }, s) => {
    const w = BrowserWindow.getAllWindows()[0]
    w.setMinimumSize(1, 1)
    w.setContentSize(s.width, s.height)
  }, { width, height })
}
