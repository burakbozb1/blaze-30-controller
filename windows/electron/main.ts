import { BrowserWindow, app, ipcMain, session, shell } from 'electron'
import * as path from 'node:path'
import { CHOOSER_PATIENCE_MS, LINKEDIN_URL, isAllowedExternal, isAllowedPermission, pickBlaze } from '../src/shell/guards'

// UI tests only (never in the packaged app): the page wires a fake amp instead of Bluetooth
const FAKE = !app.isPackaged && process.env.BLAZE_FAKE === '1'

let win: BrowserWindow | null = null

function createWindow(): void {
  win = new BrowserWindow({
    width: 1100, height: 680, minWidth: 800, minHeight: 480,
    backgroundColor: '#0d0d0f', title: 'Blaze 30 Controller', show: false, autoHideMenuBar: true,
    icon: path.join(__dirname, '../../build/icon.ico'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true, nodeIntegration: false, sandbox: true, spellcheck: false, webSecurity: true,
    },
  })
  win.removeMenu()
  win.once('ready-to-show', () => win?.show())
  const wc = win.webContents

  // The app never navigates and never opens windows.
  wc.setWindowOpenHandler(() => ({ action: 'deny' }))
  wc.on('will-navigate', (e) => e.preventDefault())

  // Web Bluetooth: no chooser UI; the first BLAZE-30 that shows up is selected, otherwise the request is cancelled.
  wc.on('select-bluetooth-device', (event, devices, callback) => {
    event.preventDefault()
    latest = callback
    const hit = pickBlaze(devices)
    if (hit) { clearGiveUp(); callback(hit.deviceId); return }
    armGiveUp()
  })
  let latest: ((id: string) => void) | null = null
  let giveUp: NodeJS.Timeout | null = null
  const clearGiveUp = () => { if (giveUp) { clearTimeout(giveUp); giveUp = null } }
  const armGiveUp = () => {
    if (giveUp) return
    giveUp = setTimeout(() => { giveUp = null; latest?.('') }, CHOOSER_PATIENCE_MS)
  }

  const html = path.join(__dirname, '../../dist/index.html')
  void win.loadFile(html, FAKE ? { query: { fake: '1' } } : undefined)
  // Web Bluetooth needs a user gesture for the first request: start the first scan as if the user had clicked.
  wc.once('did-finish-load', () => { void wc.executeJavaScript('window.__blazeStart && window.__blazeStart()', true) })
  win.on('closed', () => { win = null })
}

if (!app.requestSingleInstanceLock()) {
  app.quit() // the amp accepts one connection: a second window could only fight the first
} else {
  app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus() } })
  void app.whenReady().then(() => {
    const ses = session.defaultSession
    ses.setPermissionRequestHandler((wc, permission, cb, details) => {
      cb(wc === win?.webContents && isAllowedPermission(permission, details as { mediaTypes?: string[] }))
    })
    ses.setPermissionCheckHandler((wc, permission, _origin, details) =>
      wc === win?.webContents && isAllowedPermission(permission, { mediaTypes: [(details as { mediaType?: string }).mediaType ?? ''] }))
    ipcMain.on('open-linkedin', (e) => {
      if (!win || e.sender !== win.webContents || !isAllowedExternal(LINKEDIN_URL)) return
      if (FAKE) { // UI tests: count the request instead of opening a browser
        const g = globalThis as { __linkedinOpened?: number }
        g.__linkedinOpened = (g.__linkedinOpened ?? 0) + 1
        return
      }
      void shell.openExternal(LINKEDIN_URL)
    })
    createWindow()
  })
  app.on('window-all-closed', () => app.quit())
}
