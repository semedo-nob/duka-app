const { contextBridge, ipcRenderer } = require('electron');

const apiArg = process.argv.find((arg) => arg.startsWith('--duka-api='));
const versionArg = process.argv.find((arg) => arg.startsWith('--duka-version='));

contextBridge.exposeInMainWorld('dukaDesktop', {
  apiBase: apiArg ? apiArg.slice('--duka-api='.length) : '',
  version: versionArg ? versionArg.slice('--duka-version='.length) : '',
  printReceipt: (text) => ipcRenderer.invoke('print-receipt', text),
  listPrinters: () => ipcRenderer.invoke('list-printers'),
  printerStatus: () => ipcRenderer.invoke('printer-status'),
  testPrint: () => ipcRenderer.invoke('test-print'),
  setPrinter: (name) => ipcRenderer.invoke('set-printer', name),
});
