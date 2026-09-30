const { app, BrowserWindow, ipcMain, shell } = require('electron');
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');

const APP_VERSION = '0.3.0';
const devMode = process.env.DUKA_DEV === '1';
const devUrl = process.env.DUKA_DEV_URL || 'http://localhost:5173';

function printerFile() {
  return path.join(app.getPath('userData'), 'printer.json');
}

function configuredPrinter() {
  if (process.env.DUKA_PRINTER) return process.env.DUKA_PRINTER;
  try {
    const saved = JSON.parse(fs.readFileSync(printerFile(), 'utf8'));
    return typeof saved.name === 'string' ? saved.name : '';
  } catch {
    return '';
  }
}

function safePrinterName(name) {
  return typeof name === 'string' && /^[A-Za-z0-9][A-Za-z0-9 ._-]{0,80}$/.test(name);
}

function run(command, args, stdin) {
  return new Promise((resolve) => {
    const child = spawn(command, args, { stdio: ['pipe', 'pipe', 'pipe'] });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => { stdout += chunk.toString(); });
    child.stderr.on('data', (chunk) => { stderr += chunk.toString(); });
    child.on('error', (err) => resolve({ code: -1, stdout, stderr: err.message }));
    child.on('close', (code) => resolve({ code, stdout, stderr }));
    if (stdin != null) child.stdin.write(stdin);
    child.stdin.end();
  });
}

function contentType(file) {
  if (file.endsWith('.html')) return 'text/html; charset=utf-8';
  if (file.endsWith('.js')) return 'text/javascript; charset=utf-8';
  if (file.endsWith('.css')) return 'text/css; charset=utf-8';
  if (file.endsWith('.svg')) return 'image/svg+xml';
  if (file.endsWith('.json')) return 'application/json';
  if (file.endsWith('.png')) return 'image/png';
  if (file.endsWith('.woff2')) return 'font/woff2';
  return 'application/octet-stream';
}

function startStaticServer(root) {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      const url = new URL(req.url || '/', 'http://127.0.0.1');
      const requested = path.normalize(decodeURIComponent(url.pathname)).replace(/^[/\\]+/, '');
      const target = path.join(root, requested === '' ? 'index.html' : requested);
      if (!target.startsWith(root)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
      }
      fs.readFile(target, (err, data) => {
        if (err) {
          fs.readFile(path.join(root, 'index.html'), (fallbackErr, fallback) => {
            if (fallbackErr) {
              res.writeHead(404);
              res.end('Not found');
              return;
            }
            res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
            res.end(fallback);
          });
          return;
        }
        res.writeHead(200, { 'Content-Type': contentType(target) });
        res.end(data);
      });
    });
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      resolve({ server, origin: `http://127.0.0.1:${address.port}` });
    });
    server.on('error', reject);
  });
}

async function createWindow() {
  let origin = devUrl;
  if (!devMode) {
    const root = path.join(__dirname, '../dist');
    const started = await startStaticServer(root);
    origin = started.origin;
  }
  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      additionalArguments: [`--duka-api=${process.env.DUKA_API_URL || ''}`, `--duka-version=${APP_VERSION}`],
    },
  });
  const allowed = origin;
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://checkout.paystack.com') || url.startsWith('https://paystack.com')) {
      shell.openExternal(url);
    }
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (event, url) => {
    if (url.startsWith(allowed)) return;
    event.preventDefault();
    if (url.startsWith('https://checkout.paystack.com') || url.startsWith('https://paystack.com')) {
      shell.openExternal(url);
    }
  });
  if (devMode) win.loadURL(devUrl);
  else win.loadURL(origin);
}

ipcMain.handle('print-receipt', async (_event, text) => {
  if (typeof text !== 'string' || text.length === 0 || text.length > 8000) {
    return { ok: false, detail: 'Receipt text was rejected' };
  }
  const printer = configuredPrinter();
  if (!printer) {
    return { ok: false, detail: 'No printer is configured. Set DUKA_PRINTER to a CUPS queue name.' };
  }
  const result = await run('lp', ['-d', printer, '-o', 'raw'], text);
  if (result.code === 0) return { ok: true, detail: `Sent to printer ${printer}` };
  return { ok: false, detail: result.stderr || `lp exited ${result.code}` };
});

ipcMain.handle('list-printers', async () => {
  const result = await run('lpstat', ['-p']);
  if (result.code !== 0) return { ok: false, printers: [], detail: result.stderr || 'lpstat failed' };
  const printers = result.stdout.split('\n').map((line) => {
    const match = line.match(/^printer\s+(\S+)/);
    return match ? match[1] : '';
  }).filter(Boolean);
  return { ok: true, printers, selected: configuredPrinter() };
});

ipcMain.handle('printer-status', async () => {
  const printer = configuredPrinter();
  if (!printer) return 'No CUPS printer configured';
  const result = await run('lpstat', ['-p', printer]);
  if (result.code !== 0) return `Printer ${printer} is not available. ${result.stderr || ''}`.trim();
  return result.stdout.trim() || `Printer ${printer} responded`;
});

ipcMain.handle('test-print', async () => {
  const printer = configuredPrinter();
  if (!printer) return { ok: false, detail: 'No printer is configured. Set DUKA_PRINTER to a CUPS queue name.' };
  const result = await run('lp', ['-d', printer, '-o', 'raw'], 'Duka test print\nIf you can read this, the queue accepted the job.\n');
  if (result.code === 0) return { ok: true, detail: `Test page accepted by ${printer}` };
  return { ok: false, detail: result.stderr || `lp exited ${result.code}` };
});

ipcMain.handle('set-printer', async (_event, name) => {
  if (!safePrinterName(name)) return { ok: false, detail: 'That printer name was rejected' };
  const listed = await run('lpstat', ['-p', name]);
  if (listed.code !== 0) return { ok: false, detail: 'That printer is not in CUPS' };
  fs.mkdirSync(app.getPath('userData'), { recursive: true });
  fs.writeFileSync(printerFile(), JSON.stringify({ name }));
  return { ok: true, detail: `Using ${name}` };
});

app.whenReady().then(createWindow);
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
