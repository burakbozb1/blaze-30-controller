import { contextBridge, ipcRenderer } from 'electron'

// The only thing the page can ask of the system: open the developer's LinkedIn page (the URL lives in the main process).
contextBridge.exposeInMainWorld('blaze', {
  openLinkedIn: () => ipcRenderer.send('open-linkedin'),
})
