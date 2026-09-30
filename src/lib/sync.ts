import { api, authToken } from './api';
import { countByStatus, ensureIdentity, listDue, listPending, markFailed, markSynced, readMeta, recordLocalSale, writeMeta, type OutboxEntry } from './localDb';
import { readQueue, removeQueued } from './offlineQueue';

let imported = false;

export type SyncSnapshot = {
  online: boolean;
  lastSuccessAt?: string;
  lastError?: string;
  pending: number;
  failed: number;
  oldestPendingAt?: string;
  deviceId?: string;
  installationId?: string;
  businessId?: string;
};

async function importLegacyQueue() {
  if (imported) return;
  imported = true;
  for (const row of readQueue()) {
    await recordLocalSale(row.idempotencyKey, row.body as OutboxEntry['body']);
    removeQueued(row.idempotencyKey);
  }
}

function isNetwork(err: unknown) {
  const message = err instanceof Error ? err.message : '';
  return err instanceof TypeError || /failed to fetch|network/i.test(message);
}

export async function syncSnapshot(online: boolean): Promise<SyncSnapshot> {
  const identity = await ensureIdentity();
  const saved = await readMeta<Pick<SyncSnapshot, 'lastSuccessAt' | 'lastError'>>('sync');
  const pendingRows = await listPending();
  const oldest = pendingRows.map((row) => row.createdAt).sort()[0];
  return {
    online,
    lastSuccessAt: saved?.lastSuccessAt,
    lastError: saved?.lastError,
    pending: await countByStatus('pending'),
    failed: await countByStatus('failed'),
    oldestPendingAt: oldest,
    deviceId: identity.deviceId,
    installationId: identity.installationId,
    businessId: identity.businessId,
  };
}

export async function publishHeartbeat(snapshot: SyncSnapshot) {
  if (!authToken() || !snapshot.deviceId || !snapshot.installationId) return;
  const desktop = (window as Window & { dukaDesktop?: { printerStatus?: () => Promise<string> } }).dukaDesktop;
  let printerStatus = desktop ? 'Desktop printer bridge' : 'Browser print dialog only';
  if (desktop?.printerStatus) {
    try {
      printerStatus = await desktop.printerStatus();
    } catch {
      printerStatus = 'Printer status unavailable';
    }
  }
  try {
    await api.devices.heartbeat({
      deviceId: snapshot.deviceId,
      installationId: snapshot.installationId,
      appVersion: '0.3.0',
      pendingSync: snapshot.pending,
      failedSync: snapshot.failed,
      lastError: snapshot.lastError || '',
      printerStatus,
      scannerStatus: 'USB keyboard wedge',
      oldestPendingAt: snapshot.oldestPendingAt,
    });
  } catch {
    // The local sale remains either way. Heartbeat is diagnostics, not the sale.
  }
}

export async function flushOutbox(options?: { manual?: boolean }) {
  await importLegacyQueue();
  if (!authToken()) return;
  const identity = await ensureIdentity();
  const rows = options?.manual ? (await listDue(Number.MAX_SAFE_INTEGER)) : await listDue();
  let lastError: string | undefined;
  let lastSuccessAt = (await readMeta<SyncSnapshot>('sync'))?.lastSuccessAt;
  for (const row of rows) {
    if (row.attempts >= 20 && !options?.manual) {
      await markFailed(row.idempotencyKey, row.lastError || 'Retry limit reached. A manager can retry this sale.');
      continue;
    }
    try {
      const sale = await api.sales.create(row.body, row.idempotencyKey, {
        deviceId: identity.deviceId,
        installationId: identity.installationId,
        businessId: identity.businessId,
        clientSaleNo: row.clientSaleNo,
      });
      await markSynced(row.idempotencyKey, sale.id);
      lastSuccessAt = new Date().toISOString();
      lastError = undefined;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Sync failed';
      lastError = message;
      if (isNetwork(err) || /Request failed: 5/.test(message)) {
        const { bumpAttempt } = await import('./localDb');
        await bumpAttempt(row.idempotencyKey, message);
        break;
      }
      await markFailed(row.idempotencyKey, message);
    }
  }
  await writeMeta('sync', { lastSuccessAt, lastError });
}
